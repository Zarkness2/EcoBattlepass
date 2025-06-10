package com.exanthiax.xbattlepass.gui

import com.exanthiax.xbattlepass.api.getTier
import com.exanthiax.xbattlepass.battlepass.BattlePass
import com.exanthiax.xbattlepass.gui.components.BattleTierComponent
import com.exanthiax.xbattlepass.plugin
import com.exanthiax.xbattlepass.utils.SoundUtils
import com.willfp.eco.core.gui.menu
import com.willfp.eco.core.gui.menu.MenuLayer
import com.willfp.eco.core.gui.page.PageChanger
import com.willfp.eco.core.gui.slot
import com.willfp.eco.core.gui.slot.ConfigSlot
import com.willfp.eco.core.gui.slot.FillerMask
import com.willfp.eco.core.gui.slot.MaskItems
import com.willfp.eco.core.items.Items
import com.willfp.eco.core.items.builder.ItemStackBuilder
import com.willfp.eco.util.formatEco
import org.bukkit.entity.Player

object BattleTiersGUI {
    fun createAndOpen(player: Player, pass: BattlePass, backButton: Boolean = false) {
        val maskPattern = plugin.configYml.getStrings("tiers-gui.mask.pattern").toTypedArray()
        val maskItems = MaskItems.fromItemNames(plugin.configYml.getStrings("tiers-gui.mask.materials"))

        val levelComponent = BattleTierComponent(plugin, pass)

        val menu = menu(maskPattern.size) {
            title = plugin.configYml.getString("tiers-gui.title")
                .replace("%pass%", pass.name)
                .formatEco()

            maxPages(levelComponent.pages)

            setMask(FillerMask(maskItems, *maskPattern))

            addComponent(1, 1, levelComponent)

            defaultPage {
                levelComponent.getPageOf(it.getTier(pass)).coerceAtLeast(1)
            }

            // Instead of the page changer, this will show up when on the first page
            if (backButton) {
                addComponent(
                    MenuLayer.LOWER,
                    plugin.configYml.getInt("tiers-gui.buttons.prev-page.location.row"),
                    plugin.configYml.getInt("tiers-gui.buttons.prev-page.location.column"),
                    slot(
                        ItemStackBuilder(Items.lookup(plugin.configYml.getString("tiers-gui.buttons.prev-page.material")))
                            .setDisplayName(plugin.configYml.getString("tiers-gui.buttons.prev-page.name"))
                            .build()
                    ) {
                        onLeftClick { _, _ ->
                            BattlePassGUI.createAndOpen(player, pass) }
                    }
                )
            }

            addComponent(
                plugin.configYml.getInt("tiers-gui.buttons.prev-page.location.row"),
                plugin.configYml.getInt("tiers-gui.buttons.prev-page.location.column"),
                PageChanger(
                    ItemStackBuilder(Items.lookup(plugin.configYml.getString("tiers-gui.buttons.prev-page.material")))
                        .setDisplayName(plugin.configYml.getString("tiers-gui.buttons.prev-page.name"))
                        .build(),
                    PageChanger.Direction.BACKWARDS
                )
            )

            addComponent(
                plugin.configYml.getInt("tiers-gui.buttons.next-page.location.row"),
                plugin.configYml.getInt("tiers-gui.buttons.next-page.location.column"),
                PageChanger(
                    ItemStackBuilder(Items.lookup(plugin.configYml.getString("tiers-gui.buttons.next-page.material")))
                        .setDisplayName(plugin.configYml.getString("tiers-gui.buttons.next-page.name"))
                        .build(),
                    PageChanger.Direction.FORWARDS
                )
            )

            setSlot(
                plugin.configYml.getInt("tiers-gui.buttons.close.location.row"),
                plugin.configYml.getInt("tiers-gui.buttons.close.location.column"),
                slot(
                    ItemStackBuilder(Items.lookup(plugin.configYml.getString("tiers-gui.buttons.close.material")))
                        .setDisplayName(plugin.configYml.getString("tiers-gui.buttons.close.name"))
                        .build()
                ) {
                    onLeftClick { event, _ ->
                        event.whoClicked.closeInventory() }
                }
            )

            for (config in plugin.configYml.getSubsections("tiers-gui.buttons.custom-slots")) {
                setSlot(
                    config.getInt("row"),
                    config.getInt("column"),
                    ConfigSlot(config)
                )
            }
        }

        menu.open(player)
    }
}