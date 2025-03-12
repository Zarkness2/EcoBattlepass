package ru.oftendev.xbattlepass

import com.willfp.eco.core.config.BaseConfig
import com.willfp.eco.core.config.ConfigType
import com.willfp.libreforge.loader.LibreforgePlugin

lateinit var plugin: LibreforgePlugin
    private set

class XBattlePass: LibreforgePlugin() {
    val battlePassYml = BattlePassYml(this)

    init {
        plugin = this
        this.configHandler.addConfig(battlePassYml)
    }
}

class BattlePassYml(plugin: LibreforgePlugin): BaseConfig(
    "battlepasss",
    plugin,
    true,
    ConfigType.YAML
)