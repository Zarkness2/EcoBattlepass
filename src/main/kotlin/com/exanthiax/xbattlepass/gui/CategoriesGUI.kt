package com.exanthiax.xbattlepass.gui

import com.willfp.eco.core.gui.menu.Menu
import com.willfp.eco.core.gui.slot.ConfigSlot
import com.willfp.eco.core.gui.slot.FillerMask
import com.willfp.eco.core.gui.slot.MaskItems
import com.willfp.eco.core.gui.slot.Slot
import com.willfp.eco.core.items.Items
import com.willfp.eco.core.items.builder.ItemStackBuilder
import org.bukkit.entity.Player
import com.exanthiax.xbattlepass.battlepass.BattlePass
import com.exanthiax.xbattlepass.categories.Category
import com.exanthiax.xbattlepass.plugin

class CategoriesGUI(private val player: Player, val pass: BattlePass,
                    val page: Int = 1, val backButton: Boolean = false) {
    fun open() {
        val pattern = plugin.configYml.getStrings("categories-gui.mask.pattern")
        val menu = Menu.builder(pattern.size)
            .setTitle(plugin.configYml.getFormattedString("categories-gui.title")
                .replace("%page%", page.toString())
                .replace("%pass%", pass.name)
            )
        var row = 1
        var num = ((page-1)*getPerPage())
        pattern.forEach {
            var col = 1
            it.toCharArray().forEach {
                    s -> kotlin.run {
                if (s.equals('c', true)) {
                    if (num < pass.categories.size) {
                        menu.setSlot(row, col, slot(pass.categories.toList()[num]))
                    }
                    num++
                }
            }
                col++
            }
            row++
        }
        menu.setMask(
            FillerMask(
                MaskItems.fromItemNames(plugin.configYml.getStrings("categories-gui.mask.items")),
                *pattern.toTypedArray()
            )
        )
        menu.setSlot(
            plugin.configYml.getInt("categories-gui.next-page.row"),
            plugin.configYml.getInt("categories-gui.next-page.column"),
            nextSlot()
        )
        menu.setSlot(
            plugin.configYml.getInt("categories-gui.prev-page.row"),
            plugin.configYml.getInt("categories-gui.prev-page.column"),
            prevSlot()
        )
        for (config in plugin.configYml.getSubsections("categories-gui.custom-slots")) {
            menu.setSlot(
                config.getInt("row"),
                config.getInt("column"),
                ConfigSlot(config)
            )
        }
        menu.build().open(player)
    }

    private fun getPerPage(): Int {
        return plugin.configYml.getStrings("categories-gui.mask.pattern")
            .sumOf {
                it.toCharArray().filter { it1 -> it1.equals('c', true) }.size
            }
    }

    private fun getMaxPages(): Int {
        val total = pass.categories.size
        return total/getPerPage()
    }

    private fun nextSlot(): Slot {
        val nextActive = page < getMaxPages()
        val builder = Slot.builder(
            ItemStackBuilder(
                Items.lookup(plugin.configYml.getString("categories-gui.next-page.item.${getActive(nextActive)}"))
            ).addLoreLines(
                plugin.configYml.getFormattedStrings("categories-gui.next-page.lore.${getActive(nextActive)}")
            ).build()
        )
        if (nextActive) {
            builder.onLeftClick { _, _ ->
                run {
                    CategoriesGUI(player, pass, page + 1, backButton).open()
                }
            }
        }
        return builder.build()
    }

    private fun prevSlot(): Slot {
        val prevActive = page > 1 || backButton
        val builder = Slot.builder(
            ItemStackBuilder(
                Items.lookup(plugin.configYml.getString("categories-gui.prev-page.item.${getActive(prevActive)}"))
            ).addLoreLines(
                plugin.configYml.getFormattedStrings("categories-gui.prev-page.lore.${getActive(prevActive)}")
            ).build()
        )
        if (prevActive) {
            builder.onLeftClick { _, _ ->
                run {
                    if (page > 1) {
                        CategoriesGUI(player, pass, page - 1, backButton).open()
                    } else if (backButton) {
                        BattlePassGUI.createAndOpen(player, pass)
                    }
                }
            }
        }
        return builder.build()
    }

    private fun getActive(active: Boolean): String {
        return if (active) "active" else "inactive"
    }

    private fun slot(pair: Category): Slot {
        val itemBuilder = ItemStackBuilder(
            pair.getDisplayItem(player)
        )

        return Slot.builder(
            itemBuilder.build()
        )
            .onLeftClick { _, _, _ ->
                if (pair.isActive) {
                    QuestsGUI(player, pair, wasBack = backButton).open()
                }
            }
            .build()
    }
}