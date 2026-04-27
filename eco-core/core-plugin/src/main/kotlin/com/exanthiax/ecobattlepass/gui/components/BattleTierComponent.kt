package com.exanthiax.ecobattlepass.gui.components

import com.exanthiax.ecobattlepass.api.getTier
import com.exanthiax.ecobattlepass.api.hasPremium
import com.exanthiax.ecobattlepass.api.hasReceivedTier
import com.exanthiax.ecobattlepass.api.receiveTier
import com.exanthiax.ecobattlepass.api.receiveTierFreeOnly
import com.exanthiax.ecobattlepass.api.receiveTierPremiumOnly
import com.exanthiax.ecobattlepass.battlepass.BattlePass
import com.exanthiax.ecobattlepass.tiers.BPTier
import com.exanthiax.ecobattlepass.tiers.TierType
import com.exanthiax.ecobattlepass.utils.InternalPlaceholders
import com.exanthiax.ecobattlepass.utils.ReceivedTierState
import com.github.benmanes.caffeine.cache.Caffeine
import com.willfp.eco.core.EcoPlugin
import com.willfp.eco.core.gui.menu.Menu
import com.willfp.eco.core.items.Items
import com.willfp.eco.core.items.builder.ItemStackBuilder
import com.willfp.eco.core.map.nestedMap
import com.willfp.eco.core.placeholder.context.placeholderContext
import com.willfp.eco.core.sound.PlayableSound
import com.willfp.eco.util.NumberUtils.evaluateExpression
import com.willfp.eco.util.formatEco
import com.willfp.eco.util.openMenu
import com.willfp.ecomponent.components.LevelState
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

private val levelItemCache = Caffeine.newBuilder()
    .expireAfterWrite(
        com.exanthiax.ecobattlepass.plugin.configYml.getInt("gui-cache-ttl").toLong(),
        TimeUnit.MILLISECONDS
    )
    .build<Pair<UUID, Int>, ItemStack>()

class BattleTierComponent(
    private val plugin: EcoPlugin,
    private val pass: BattlePass,
    private val tierType: TierType? = null,
    patternPath: String = "tiers-gui.mask.progression-pattern"
) : ProperLevelComponent() {
    override val pattern: List<String> = plugin.configYml.getStrings(patternPath)
    override val maxLevel = pass.maxLevel

    private val showEmptyAsClaimed = plugin.configYml.getBool("tiers-gui.show-empty-tiers-as-claimed")
    private val itemCache = nestedMap<LevelState, Int, ItemStack>()

    private fun hasRelevantRewards(tier: BPTier): Boolean {
        return when (tierType) {
            TierType.FREE -> tier.rewards.any { it.tier == TierType.FREE }
            TierType.PREMIUM -> tier.rewards.any { it.tier == TierType.PREMIUM }
            null -> tier.rewards.isNotEmpty()
        }
    }

    private fun resolveKey(player: Player, level: Int, levelState: LevelState): String {
        val tier = pass.getTier(level)

        if (tier == null || !hasRelevantRewards(tier)) {
            return when {
                showEmptyAsClaimed -> "claimed"
                else -> "hidden"
            }
        }

        if (levelState != LevelState.UNLOCKED) {
            return levelState.key
        }

        return when (player.hasReceivedTier(pass, level)) {
            ReceivedTierState.RECEIVED -> "claimed"
            ReceivedTierState.RECEIVED_FREE -> {
                if (player.hasPremium(pass)) "unlocked-free" else "premium-required"
            }
            else -> levelState.key
        }
    }

    override fun getLevelItem(player: Player, menu: Menu, level: Int, levelState: LevelState): ItemStack {
        val key = resolveKey(player, level, levelState)

        if (key == "hidden") {
            return ItemStack.empty()
        }

        fun item() = levelItemCache.get(Pair(player.uniqueId, level)) {
            val tier = pass.getTier(level) ?: BPTier(level, pass)

            val displayItem = tier.config.getStringOrNull("display.$key.item")
                ?: plugin.configYml.getString("tiers-gui.buttons.$key.item")
            val displayName = tier.config.getStringOrNull("display.$key.name")
                ?: plugin.configYml.getString("tiers-gui.buttons.$key.name")
            val displayLore = if (tier.config.has("display.$key.lore"))
                tier.config.getStrings("display.$key.lore")
            else
                plugin.configYml.getStrings("tiers-gui.buttons.$key.lore")

            val resolvedItem = InternalPlaceholders.TierPlaceholders.replace(displayItem, tier, pass, player)

            ItemStackBuilder(Items.lookup(resolvedItem))
                .setDisplayName(
                    tier.format(displayName, player).firstOrNull() ?: ""
                )
                .addLoreLines(
                    tier.format(displayLore, player)
                )
                .setAmount(
                    evaluateExpression(
                        plugin.configYml.getString("tiers-gui.buttons.item-amount")
                            .replace("%level%", level.toString()),
                        placeholderContext(player = player)
                    ).roundToInt()
                )
                .build()
        }

        return if (levelState != LevelState.IN_PROGRESS) {
            itemCache[levelState].getOrPut(level) { item() }
        } else {
            item()
        }
    }

    override fun getLevelState(player: Player, level: Int): LevelState {
        return when {
            level <= player.getTier(pass) -> LevelState.UNLOCKED
            level == player.getTier(pass) + 1 -> LevelState.IN_PROGRESS
            else -> LevelState.LOCKED
        }
    }

    override fun getLeftClickAction(player: Player, level: Int, levelState: LevelState): () -> Unit {
        val key = resolveKey(player, level, levelState)

        return when (key) {
            "unlocked", "unlocked-free", "premium-required" -> {
                {
                    val tier = pass.getTier(level)
                    if (tier != null) {
                        if (key == "premium-required") {
                            val name = tier.rewards.first { it.tier.name.equals("premium", true) }
                                .reward.getDisplayName(player)
                                .formatEco(player, true)

                            player.sendMessage(
                                InternalPlaceholders.TierPlaceholders
                                    .replace(plugin.langYml.getMessage("premium-required"), tier, pass, player)
                                    .replace("%tier%", level.toString())
                                    .replace("%reward%", name)
                            )
                            PlayableSound.create(plugin.configYml.getSubsection("sound.premium-required"))
                                ?.playTo(player)
                        } else {
                            levelItemCache.invalidate(Pair(player.uniqueId, level))
                            itemCache[levelState].remove(level)

                            when (tierType) {
                                TierType.FREE -> player.receiveTierFreeOnly(tier)
                                TierType.PREMIUM -> player.receiveTierPremiumOnly(tier)
                                null -> {
                                    if (key == "unlocked") player.receiveTier(tier)
                                    else player.receiveTierPremiumOnly(tier)
                                }
                            }

                            player.openMenu?.refresh(player)
                        }
                    }
                }
            }
            "locked", "in-progress" -> {
                { PlayableSound.create(plugin.configYml.getSubsection("sound.reward-locked"))?.playTo(player) }
            }
            else -> {
                {}
            }
        }
    }
}