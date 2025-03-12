package ru.oftendev.xbattlepass.tasks

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.registry.Registrable
import com.willfp.libreforge.ViolationContext
import com.willfp.libreforge.counters.Counters
import ru.oftendev.xbattlepass.plugin

class BattleTask(private val _id: String, val config: Config): Registrable {
    override fun getID(): String {
        return this._id
    }

    private val xpGainMethods = config.getSubsections("xp-gain-methods").mapNotNull {
        Counters.compile(it, ViolationContext(plugin, "xBattlepass task $id"))
    }


}