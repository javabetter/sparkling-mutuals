package net.johnceo.sparklingmutuals.history

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.johnceo.sparklingmutuals.safari.SafariRules
import net.minecraft.client.Minecraft

/**
 * Counts Safari tickets from the Safari Manager waving you through. Only his own line counts: it has to start with
 * "[NPC] Safari Manager:", which party, guild and private messages never do.
 */
object TicketCounter {
    private const val MANAGER = "[NPC] Safari Manager: "
    private val lines = setOf("Looks good to me. Have fun out there!", "I already saw your ticket, so you're free to go.")
    fun isTicket(text: String) = text.startsWith(MANAGER) && text.removePrefix(MANAGER).trim() in lines

    fun register() {
        ClientReceiveMessageEvents.GAME.register { message, overlay ->
            if (!overlay && isTicket(SafariRules.strip(message.string))) Minecraft.getInstance().execute {
                SparklingLog.onTicket(); RunHistory.onTicket(); Achievements.onTicket()
            }
        }
    }
}
