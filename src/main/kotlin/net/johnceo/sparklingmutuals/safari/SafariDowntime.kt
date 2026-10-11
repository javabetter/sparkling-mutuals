package net.johnceo.sparklingmutuals.safari

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.johnceo.sparklingmutuals.LocalChat
import net.johnceo.sparklingmutuals.commands.PartyChat
import net.johnceo.sparklingmutuals.config.ConfigManager
import net.minecraft.client.Minecraft

/**
 * `!dt [reason]` in party chat: someone wants a break after this run. The request waits until the run ends, then the
 * requester's own client posts "X has requested downtime" (one client, not every member's) and every client holds the
 * Safari Manager shut until the requester says `r`, or someone sneak-clicks past it once. Asked outside the Safari,
 * it takes effect straight away.
 */
object SafariDowntime {
    private val request = Regex("""^!dt(?:\s+(.+))?$""", RegexOption.IGNORE_CASE)
    private val resume = Regex("""^r$""", RegexOption.IGNORE_CASE)
    /** Requester -> reason (may be empty), in the order they asked. */
    private val requests = linkedMapOf<String, String>()
    var holding = false
        private set
    private var wasInSafari = false

    fun register() {
        ClientReceiveMessageEvents.GAME.register { message, overlay ->
            if (!overlay) Minecraft.getInstance().execute {
                val client = Minecraft.getInstance()
                chat(message.string, SafariAssist.inSafari, client.player?.name?.string.orEmpty())?.let(::send)
            }
        }
    }

    /** Handles one chat line; returns a party message this client should send, if any. */
    fun chat(text: String, inSafari: Boolean, self: String): String? {
        if (!ConfigManager.downtimeCommand) return null
        val chat = PartyChat.parse(text) ?: return null
        request.matchEntire(chat.body)?.let { match ->
            val reason = match.groupValues[1].trim()
            requests[chat.sender] = reason
            tell("[SM] ${chat.sender} wants downtime after this run" + if (reason.isEmpty()) "." else ": $reason")
            return if (!inSafari) leftSafari(self) else null
        }
        if (holding && resume.matches(chat.body)) {
            val key = requests.keys.firstOrNull { it.equals(chat.sender, true) } ?: return null
            requests.remove(key)
            if (requests.isEmpty()) holding = false
            tell("[SM] ${chat.sender} is back" + if (holding) "; still waiting on ${holders()}." else ". Safari Manager released.")
        }
        return null
    }

    /** The run is over: banked requests become a hold. Returns the requester's own announcement, if this is them. */
    fun leftSafari(self: String): String? {
        if (requests.isEmpty() || holding) return null
        holding = true
        tell("[SM] Safari Manager held for downtime. Say r in party chat when ready (sneak-click to skip).")
        val mine = requests.entries.firstOrNull { it.key.equals(self, true) } ?: return null
        return "${mine.key} has requested downtime" + if (mine.value.isEmpty()) "" else " for reason: ${mine.value}"
    }

    fun onClientTick(client: Minecraft) {
        val inSafari = SafariAssist.inSafari
        if (wasInSafari && !inSafari) leftSafari(client.player?.name?.string.orEmpty())?.let(::send)
        wasInSafari = inSafari
    }

    fun holders() = requests.keys.joinToString(", ")
    /** The one-time bypass: a sneak-click on the manager ends the hold for good. */
    fun clear() { holding = false; requests.clear() }
    fun reset() { clear(); wasInSafari = false }

    private fun send(message: String) { Minecraft.getInstance().player?.connection?.sendCommand("pc $message") }
    private fun tell(text: String) { Minecraft.getInstance()?.player?.sendSystemMessage(LocalChat.component(text)) }
}
