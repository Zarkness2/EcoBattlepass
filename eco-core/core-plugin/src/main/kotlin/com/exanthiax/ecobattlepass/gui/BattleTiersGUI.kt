package com.exanthiax.ecobattlepass.gui
import com.exanthiax.ecobattlepass.api.getTier
import com.exanthiax.ecobattlepass.battlepass.BattlePass
import com.exanthiax.ecobattlepass.gui.components.BattleTierComponent
import com.exanthiax.ecobattlepass.plugin
import com.exanthiax.ecobattlepass.tiers.TierType
import com.exanthiax.ecobattlepass.utils.InternalPlaceholders
import com.willfp.eco.core.gui.menu
import com.willfp.eco.core.gui.menu.MenuLayer
import com.willfp.eco.core.gui.onEvent
import com.willfp.eco.core.gui.page.PageChanger
import com.willfp.eco.core.gui.page.PageChangeEvent
import com.willfp.eco.core.gui.slot
import com.willfp.eco.core.gui.slot.ConfigSlot
import com.willfp.eco.core.gui.slot.FillerMask
import com.willfp.eco.core.gui.slot.MaskItems
import com.exanthiax.ecobattlepass.gui.components.LayoutMode
import com.willfp.eco.core.items.Items
import com.willfp.eco.core.items.builder.ItemStackBuilder
import com.willfp.eco.util.formatEco
import org.bukkit.entity.Player
import kotlin.math.max
object BattleTiersGUI {
    fun createAndOpen(player: Player, pass: BattlePass, backButton: Boolean = false) {
        val maskPattern = plugin.configYml.getStrings("tiers-gui.mask.pattern").toTypedArray()
        val maskItems = MaskItems.fromItemNames(plugin.configYml.getStrings("tiers-gui.mask.materials"))
        //val layout = plugin.configYml.getStringOrNull("tiers-gui.layout") ?: "combined"
        val layout = LayoutMode.fromConfig(
            plugin.configYml.getStringOrNull("tiers-gui.layout") ?: "combined"
        )
        val openAtCurrentTier = plugin.configYml.getBoolOrNull("tiers-gui.open-at-current-tier") ?: true
        fun r(s: String) = InternalPlaceholders.BattlePassPlaceholders.replace(s, battlepass = pass, player = player)
        // Build components based on layout
        val components: List<BattleTierComponent>
        val totalPages: Int
        when (layout) {
            LayoutMode.SPLIT -> {
                val freeComponent = BattleTierComponent(
                    plugin, pass,
                    tierType = TierType.FREE,
                    patternPath = "tiers-gui.split.free-pattern"
                )
                val premiumComponent = BattleTierComponent(
                    plugin, pass,
                    tierType = TierType.PREMIUM,
                    patternPath = "tiers-gui.split.premium-pattern"
                )
                components = listOf(freeComponent, premiumComponent)
                totalPages = max(freeComponent.pages, premiumComponent.pages)
            }
            LayoutMode.COMBINED -> {
                val levelComponent = BattleTierComponent(plugin, pass)
                components = listOf(levelComponent)
                totalPages = levelComponent.pages
            }
        }

        /*
        if (layout == LayoutMode.SPLIT) {
            val freeComponent = BattleTierComponent(
                plugin, pass,
                tierType = TierType.FREE,
                patternPath = "tiers-gui.split.free-pattern"
            )
            val premiumComponent = BattleTierComponent(
                plugin, pass,
                tierType = TierType.PREMIUM,
                patternPath = "tiers-gui.split.premium-pattern"
            )
            components = listOf(freeComponent, premiumComponent)
            totalPages = max(freeComponent.pages, premiumComponent.pages)
        } else {
            val levelComponent = BattleTierComponent(plugin, pass)
            components = listOf(levelComponent)
            totalPages = levelComponent.pages
        }
        */
        // Calculate the default page before building the menu (needed for initial title)
        val defaultPageNum = if (openAtCurrentTier) {
            components.first().getPageOf(player.getTier(pass)).coerceAtLeast(1)
        } else {
            1
        }

        val menu = menu(maskPattern.size) {
            title = plugin.configYml.getString("tiers-gui.title")
                .replace("%pass%", pass.name)
                .formatEco()
            maxPages(totalPages)
            setMask(FillerMask(maskItems, *maskPattern))
            components.forEach { addComponent(1, 1, it) }
            if (openAtCurrentTier) {
                defaultPage {
                    components.first().getPageOf(it.getTier(pass)).coerceAtLeast(1)
                }
            }
            // Back button (shows on first page instead of prev-page)
            if (backButton) {
                addComponent(
                    MenuLayer.LOWER,
                    plugin.configYml.getInt("tiers-gui.buttons.prev-page.location.row"),
                    plugin.configYml.getInt("tiers-gui.buttons.prev-page.location.column"),
                    slot(
                        ItemStackBuilder(Items.lookup(r(plugin.configYml.getString("tiers-gui.buttons.prev-page.material"))))
                            .setDisplayName(r(plugin.configYml.getString("tiers-gui.buttons.prev-page.name")))
                            .build()
                    ) {
                        onLeftClick { _, _ ->
                            BattlePassGUI.createAndOpen(player, pass)
                        }
                    }
                )
            }
            addComponent(
                plugin.configYml.getInt("tiers-gui.buttons.prev-page.location.row"),
                plugin.configYml.getInt("tiers-gui.buttons.prev-page.location.column"),
                PageChanger(
                    ItemStackBuilder(Items.lookup(r(plugin.configYml.getString("tiers-gui.buttons.prev-page.material"))))
                        .setDisplayName(r(plugin.configYml.getString("tiers-gui.buttons.prev-page.name")))
                        .build(),
                    PageChanger.Direction.BACKWARDS
                )
            )
            addComponent(
                plugin.configYml.getInt("tiers-gui.buttons.next-page.location.row"),
                plugin.configYml.getInt("tiers-gui.buttons.next-page.location.column"),
                PageChanger(
                    ItemStackBuilder(Items.lookup(r(plugin.configYml.getString("tiers-gui.buttons.next-page.material"))))
                        .setDisplayName(r(plugin.configYml.getString("tiers-gui.buttons.next-page.name")))
                        .build(),
                    PageChanger.Direction.FORWARDS
                )
            )
            if (plugin.configYml.getBool("tiers-gui.buttons.close.enabled"))
                setSlot(
                    plugin.configYml.getInt("tiers-gui.buttons.close.location.row"),
                    plugin.configYml.getInt("tiers-gui.buttons.close.location.column"),
                    slot(
                        ItemStackBuilder(Items.lookup(r(plugin.configYml.getString("tiers-gui.buttons.close.material"))))
                            .setDisplayName(r(plugin.configYml.getString("tiers-gui.buttons.close.name")))
                            .build()
                    ) {
                        onLeftClick { event, _ ->
                            event.whoClicked.closeInventory()
                        }
                    }
                )
            for (slotConfig in plugin.configYml.getSubsections("tiers-gui.buttons.custom-slots")) {
                val resolved = slotConfig.clone().apply {
                    set("item", r(getString("item")))
                    set("lore", getStrings("lore").map(::r))
                    listOf("left-click", "right-click", "shift-left-click", "shift-right-click").forEach { click ->
                        if (this.has(click)) {
                            this.set(click, this.getStrings(click).map(::r))
                        }
                    }
                }
                setSlot(
                    resolved.getInt("row"),
                    resolved.getInt("column"),
                    ConfigSlot(resolved)
                )
            }
        }
        menu.open(player)
    }
}
