package net.johnceo.sparklingmutuals.history

import net.johnceo.sparklingmutuals.LocalChat
import net.johnceo.sparklingmutuals.safari.SafariRules
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.player.Inventory

/**
 * All-time Safari playtime from SkyBlock's playtime menu (`/playtime`, then the result): the "Safari" item says
 * "Playtime: 2,251 minutes". It covers time from before runs were recorded, so the hours stats and achievements use it.
 */
object PlaytimeReader {
    class Data(var minutes: Long = 0, var readAt: Long = 0)
    private var data = Data()
    private val playtime = Regex("Playtime: ([\\d,]+) minutes?", RegexOption.IGNORE_CASE)
    private var ticks = 0

    fun register() { data = HistoryStore.load("playtime", Data::class.java, ::Data) }

    fun tick(client: Minecraft) {
        val screen = client.screen as? AbstractContainerScreen<*> ?: return
        if (++ticks % 10 != 0) return
        for (slot in screen.menu.slots) {
            if (slot.container is Inventory) continue
            val stack = slot.item
            if (stack.isEmpty || SafariRules.strip(stack.hoverName.string) != "Safari") continue
            val minutes = stack.get(DataComponents.LORE)?.lines()?.firstNotNullOfOrNull { playtime.find(SafariRules.strip(it.string)) }
                ?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull() ?: continue
            if (minutes != data.minutes) {
                val first = data.minutes <= 0
                data = Data(minutes, System.currentTimeMillis())
                HistoryStore.save("playtime", data)
                if (first) client.player?.sendSystemMessage(LocalChat.component("[SM] Safari playtime saved: §e%.1f hours§r.".format(minutes / 60.0)))
            }
            return
        }
    }

    val minutes get() = data.minutes
    /** The menu's figure plus runs since it was read, or the saved runs alone if that's more. */
    fun safariMillis(): Long {
        val runs = RunHistory.runs.sumOf { it.length }
        if (data.minutes <= 0) return runs
        return maxOf(runs, data.minutes * 60_000L + RunHistory.runs.filter { it.start > data.readAt }.sumOf { it.length })
    }
}
