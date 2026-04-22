package com.exanthiax.ecobattlepass.commands.dynamic

import com.exanthiax.ecobattlepass.api.getTier
import com.exanthiax.ecobattlepass.api.hasPremium
import com.exanthiax.ecobattlepass.api.hasReceivedTier
import com.exanthiax.ecobattlepass.api.receiveTier
import com.exanthiax.ecobattlepass.api.receiveTierFreeOnly
import com.exanthiax.ecobattlepass.api.receiveTierPremiumOnly
import com.exanthiax.ecobattlepass.battlepass.BattlePass
import com.exanthiax.ecobattlepass.commands.helpers.Messages
import com.exanthiax.ecobattlepass.gui.BattlePassGUI
import com.exanthiax.ecobattlepass.gui.BattleTiersGUI
import com.exanthiax.ecobattlepass.gui.QuestsGUI
import com.exanthiax.ecobattlepass.gui.components.invalidateTierItemCache
import com.exanthiax.ecobattlepass.plugin
import com.exanthiax.ecobattlepass.utils.ReceivedTierState
import com.willfp.eco.core.command.impl.PluginCommand
import com.willfp.eco.util.openMenu
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.util.StringUtil

class DynamicPassCommand(
    private val pass: BattlePass,
    cmd: String
) : PluginCommand(
    plugin,
    cmd,
    "ecobattlepass.command.$cmd",
    true  // player-only
) {
    override fun onExecute(sender: Player, args: MutableList<String>) {
        when (args.getOrNull(0)?.lowercase()) {
            null -> {
                BattlePassGUI.createAndOpen(sender, pass)
            }

            "tiers" -> {
                BattleTiersGUI.createAndOpen(sender, pass)
            }

            "quests" -> {
                val categoryId = args.getOrNull(1) ?: run {
                    Messages.sendCategoryRequired(sender)
                    return
                }

                val category = pass.categories.firstOrNull {
                    it.id.equals(categoryId, ignoreCase = true)
                } ?: run {
                    Messages.sendInvalidCategory(sender)
                    return
                }

                QuestsGUI(sender, category, wasBack = false).open()
            }

            "claim" -> {
                handleClaim(sender, args)
            }

            else -> {
                Messages.sendDynamicPassUsage(sender)
            }
        }
    }

    /** * Handles: /<pass> claim <tier|all> [free|premium]
     */
    private fun handleClaim(player: Player, args: MutableList<String>) {
        val tierArg = args.getOrNull(1)?.lowercase() ?: run {
            Messages.sendDynamicPassUsage(player)
            return
        }

        val typeArg = args.getOrNull(2)?.lowercase() // null, "free", or "premium"

        if (tierArg == "all") {
            handleClaimAll(player, typeArg)
        } else {
            val tierNumber = tierArg.toIntOrNull() ?: run {
                Messages.sendDynamicPassUsage(player)
                return
            }
            handleClaimSingle(player, tierNumber, typeArg)
        }
    }

    /** * Claims rewards for a single tier.
     */
    private fun handleClaimSingle(player: Player, tierNumber: Int, typeArg: String?) {
        // Validate tier exists
        val tier = pass.getTier(tierNumber) ?: run {
            Messages.sendTierNotFound(player, tierNumber)
            return
        }

        // Make sure the tier number matches exactly (getTier can return the next tier)
        if (tier.number != tierNumber) {
            Messages.sendTierNotFound(player, tierNumber)
            return
        }

        // Check if player has unlocked this tier
        val playerTier = player.getTier(pass)
        if (tierNumber > playerTier) {
            Messages.sendTierNotUnlocked(player, tierNumber)
            return
        }

        val receivedState = player.hasReceivedTier(pass, tierNumber)

        when (typeArg) {
            // No type specified: normal behavior
            null -> {
                when (receivedState) {
                    ReceivedTierState.RECEIVED -> {
                        Messages.sendTierAlreadyClaimed(player, tierNumber)
                        return
                    }

                    ReceivedTierState.RECEIVED_FREE -> {
                        // Free already claimed, try premium
                        if (player.hasPremium(pass)) {
                            player.receiveTierPremiumOnly(tier)
                        } else {
                            Messages.sendTierAlreadyClaimed(player, tierNumber)
                            return
                        }
                    }

                    ReceivedTierState.RECEIVED_PREMIUM -> {
                        // Premium already claimed, try free
                        player.receiveTierFreeOnly(tier)
                    }

                    ReceivedTierState.NOT_RECEIVED -> {
                        player.receiveTier(tier)
                    }
                }
            }

            "free" -> {
                when (receivedState) {
                    ReceivedTierState.RECEIVED,
                    ReceivedTierState.RECEIVED_FREE -> {
                        Messages.sendTierAlreadyClaimed(player, tierNumber)
                        return
                    }

                    ReceivedTierState.RECEIVED_PREMIUM,
                    ReceivedTierState.NOT_RECEIVED -> {
                        player.receiveTierFreeOnly(tier)
                    }
                }
            }

            "premium" -> {
                if (!player.hasPremium(pass)) {
                    Messages.sendClaimNoPremium(player)
                    return
                }
                when (receivedState) {
                    ReceivedTierState.RECEIVED,
                    ReceivedTierState.RECEIVED_PREMIUM -> {
                        Messages.sendTierAlreadyClaimed(player, tierNumber)
                        return
                    }

                    ReceivedTierState.RECEIVED_FREE,
                    ReceivedTierState.NOT_RECEIVED -> {
                        player.receiveTierPremiumOnly(tier)
                    }
                }
            }

            else -> {
                Messages.sendDynamicPassUsage(player)
                return
            }
        }

        // Invalidate cache and refresh GUI
        invalidateTierItemCache()
        player.openMenu?.refresh(player)
    }

    /** * Claims all available tier rewards.
     */
    private fun handleClaimAll(player: Player, typeArg: String?) {
        // Validate premium if requesting premium type
        if (typeArg == "premium" && !player.hasPremium(pass)) {
            Messages.sendClaimNoPremium(player)
            return
        }

        val playerTier = player.getTier(pass)
        var claimedCount = 0

        for (tier in pass.tiers) {
            if (tier.number > playerTier) continue

            val receivedState = player.hasReceivedTier(pass, tier.number)

            when (typeArg) {
                // claim all — normal behavior
                null -> {
                    when (receivedState) {
                        ReceivedTierState.RECEIVED -> continue
                        ReceivedTierState.RECEIVED_FREE -> {
                            if (player.hasPremium(pass)) {
                                player.receiveTierPremiumOnly(tier)
                                claimedCount++
                            }
                        }

                        ReceivedTierState.RECEIVED_PREMIUM -> {
                            player.receiveTierFreeOnly(tier)
                            claimedCount++
                        }

                        ReceivedTierState.NOT_RECEIVED -> {
                            player.receiveTier(tier)
                            claimedCount++
                        }
                    }
                }

                // claim all free
                "free" -> {
                    when (receivedState) {
                        ReceivedTierState.RECEIVED,
                        ReceivedTierState.RECEIVED_FREE -> continue

                        ReceivedTierState.RECEIVED_PREMIUM,
                        ReceivedTierState.NOT_RECEIVED -> {
                            player.receiveTierFreeOnly(tier)
                            claimedCount++
                        }
                    }
                }

                // claim all premium
                "premium" -> {
                    when (receivedState) {
                        ReceivedTierState.RECEIVED,
                        ReceivedTierState.RECEIVED_PREMIUM -> continue

                        ReceivedTierState.RECEIVED_FREE,
                        ReceivedTierState.NOT_RECEIVED -> {
                            player.receiveTierPremiumOnly(tier)
                            claimedCount++
                        }
                    }
                }

                else -> {
                    Messages.sendDynamicPassUsage(player)
                    return
                }
            }
        }

        if (claimedCount == 0) {
            Messages.sendNoRewardsToClaim(player)
            return
        }

        // Invalidate cache and refresh GUI
        invalidateTierItemCache()
        player.openMenu?.refresh(player)

        // Summary message
        Messages.sendClaimAllSuccess(player, claimedCount)
    }

    override fun tabComplete(sender: CommandSender, args: List<String>): List<String> {
        return when (args.size) {
            1 -> StringUtil.copyPartialMatches(
                args[0],
                listOf("tiers", "quests", "claim"),
                mutableListOf()
            )

            2 -> when (args[0].lowercase()) {
                "quests" -> StringUtil.copyPartialMatches(
                    args[1],
                    pass.categories.map { it.id },
                    mutableListOf()
                )

                "claim" -> StringUtil.copyPartialMatches(
                    args[1],
                    pass.tiers.map { it.number.toString() } + "all",
                    mutableListOf()
                )

                else -> emptyList()
            }

            3 -> {
                if (args[0].equals("claim", ignoreCase = true)) {
                    StringUtil.copyPartialMatches(
                        args[2],
                        listOf("free", "premium"),
                        mutableListOf()
                    )
                } else emptyList()
            }

            else -> emptyList()
        }
    }
}
