package ru.oftendev.xbattlepass.api.events

import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.HandlerList
import org.bukkit.event.player.PlayerEvent
import ru.oftendev.xbattlepass.rewards.Reward

class PlayerRewardEvent(player: Player, val reward: Reward): PlayerEvent(player), Cancellable {
    companion object {
        val handlerList = HandlerList()
    }

    private var cancelled = false

    override fun getHandlers(): HandlerList {
        return handlerList
    }

    override fun isCancelled(): Boolean {
        return cancelled
    }

    override fun setCancelled(p0: Boolean) {
        cancelled = p0
    }
}