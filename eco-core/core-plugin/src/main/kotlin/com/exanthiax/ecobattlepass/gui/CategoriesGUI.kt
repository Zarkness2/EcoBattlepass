package com.exanthiax.ecobattlepass.gui

import com.exanthiax.ecobattlepass.battlepass.BattlePass
import com.exanthiax.ecobattlepass.categories.Category
import com.exanthiax.ecobattlepass.plugin
import com.exanthiax.ecobattlepass.utils.InternalPlaceholders
import com.willfp.eco.core.gui.menu.Menu
import com.willfp.eco.core.gui.slot.ConfigSlot
import com.willfp.eco.core.gui.slot.FillerMask
import com.willfp.eco.core.gui.slot.MaskItems
import com.willfp.eco.core.gui.slot.Slot
import com.willfp.eco.core.items.Items
import com.willfp.eco.core.items.builder.ItemStackBuilder
import com.willfp.eco.core.sound.PlayableSound
import org.bukkit.entity.Player

class CategoriesGUI(
    private val player: Player, val pass: BattlePass,
    val page: Int = 1, val backButton: Boolean = false
) {

    private fun r(s: String) =
        InternalPlaceholders.BattlePassPlaceholders.replace(s, battlepass = pass, player = player)

    private fun rAll(list: List<String>) =
        InternalPlaceholders.BattlePassPlaceholders.replaceAll(list, battlepass = pass, player = player)

    fun open() {
        val pattern = plugin.configYml.getStrings("categories-gui.mask.pattern")
        val menu = Menu.builder(pattern.size)
            .setTitle(
                r(
                    plugin.configYml.getString("categories-gui.title")
                        .replace("%page%", page.toString())
                )
            )
        var row = 1
        var num = ((page - 1) * getPerPage())
        pattern.forEach {
            var col = 1
            it.toCharArray().forEach { s ->
                kotlin.run {
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
            plugin.configYml.getInt("categories-gui.buttons.next-page.row"),
            plugin.configYml.getInt("categories-gui.buttons.next-page.column"),
            nextSlot()
        )
        menu.setSlot(
            plugin.configYml.getInt("categories-gui.buttons.prev-page.row"),
            plugin.configYml.getInt("categories-gui.buttons.prev-page.column"),
            prevSlot()
        )

        for (slotConfig in plugin.configYml.getSubsections("categories-gui.buttons.custom-slots")) {
            val resolved = slotConfig.clone().apply {
                val nameKey = getStringOrNull("name")
                val itemStr = r(getString("item"))
                // If there is a separate name and the item does not have an inline name, inject it
                if (nameKey != null && !itemStr.contains("name:")) {
                    set("item", "$itemStr name:\"${r(nameKey)}\"")
                } else {
                    set("item", itemStr)
                }
                set("lore", getStrings("lore").map(::r))
                listOf("left-click", "right-click", "shift-left-click", "shift-right-click").forEach { click ->
                    if (this.has(click)) {
                        this.set(click, this.getStrings(click).map(::r))
                    }
                }
            }

            menu.setSlot(
                resolved.getInt("row"),
                resolved.getInt("column"),
                ConfigSlot(resolved)
            )
        }

        if (plugin.configYml.getBool("categories-gui.buttons.close.enabled")) {
            menu.setSlot(
                plugin.configYml.getInt("categories-gui.buttons.close.row"),
                plugin.configYml.getInt("categories-gui.buttons.close.column"),
                buildCloseSlot("categories-gui.buttons.close")
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
        val perPage = getPerPage()
        if (perPage == 0) return 1
        return (total + perPage - 1) / perPage
    }

    private fun nextSlot(): Slot {
        val nextActive = page < getMaxPages()
        val state = if (nextActive) "active" else "inactive"
        val builder = Slot.builder(
            buildPageItem("categories-gui.buttons.next-page", state)
        )
        if (nextActive) {
            builder.onLeftClick { _, _ ->
                CategoriesGUI(player, pass, page + 1, backButton).open()
            }
        }
        return builder.build()
    }

    private fun prevSlot(): Slot {
        val prevActive = page > 1 || backButton
        val state = if (prevActive) "active" else "inactive"
        val builder = Slot.builder(
            buildPageItem("categories-gui.buttons.prev-page", state)
        )

        if (prevActive) {
            builder.onLeftClick { _, _ ->
                when {
                    page > 1 -> CategoriesGUI(player, pass, page - 1, backButton).open()
                    else -> BattlePassGUI.createAndOpen(player, pass)
                }
            }
        }
        return builder.build()
    }

    /**
     * Builds an ItemStack for a page changer button (next-page / prev-page).
     * Supports 3 config formats:
     *   1) item.active/inactive with inline name  (old)
     *   2) item.active/inactive + name.active/inactive  (new)
     *   3) material + name  (legacy tiers-gui style)
     */
    private fun buildPageItem(basePath: String, state: String): org.bukkit.inventory.ItemStack {
        // Item: try item.state → item → material
        val itemString = plugin.configYml.getStringOrNull("$basePath.item.$state")
            ?: plugin.configYml.getStringOrNull("$basePath.item")
            ?: plugin.configYml.getString("$basePath.material")

        val itemBuilder = ItemStackBuilder(Items.lookup(r(itemString)))

        // Name: try name.state → name (only if separate key exists)
        val name = plugin.configYml.getStringOrNull("$basePath.name.$state")
            ?: plugin.configYml.getStringOrNull("$basePath.name")
        if (name != null) {
            itemBuilder.setDisplayName(r(name))
        }

        // Lore: try lore.state → lore
        val lore = plugin.configYml.getStringsOrNull("$basePath.lore.$state")
            ?: plugin.configYml.getStringsOrNull("$basePath.lore")
            ?: emptyList()
        itemBuilder.addLoreLines(rAll(lore))

        return itemBuilder.build()
    }

    /**
     * Builds a close button slot.
     * Supports: item OR material, + name (separate key)
     */
    private fun buildCloseSlot(basePath: String): Slot {
        val itemString = plugin.configYml.getStringOrNull("$basePath.item")
            ?: plugin.configYml.getString("$basePath.material")

        val itemBuilder = ItemStackBuilder(Items.lookup(r(itemString)))

        plugin.configYml.getStringOrNull("$basePath.name")?.let {
            itemBuilder.setDisplayName(r(it))
        }

        val lore = plugin.configYml.getStringsOrNull("$basePath.lore")
            ?: emptyList()
        itemBuilder.addLoreLines(rAll(lore))

        return Slot.builder(itemBuilder.build())
            .onLeftClick { event, _ ->
                event.whoClicked.closeInventory()
            }.build()
    }

    private fun slot(pair: Category): Slot {
        val itemBuilder = ItemStackBuilder(pair.getDisplayItem(player))

        return Slot.builder(itemBuilder.build())
            .onLeftClick { _, _, _ ->
                if (pair.isActive) {
                    PlayableSound.create(plugin.configYml.getSubsection("sound.gui-click-sound"))?.playTo(player)
                    QuestsGUI(player, pair, wasBack = backButton).open()
                }
            }
            .build()
    }
}
