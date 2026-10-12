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
 * it takes effect straight away. Requests leave with their requester: someone leaving or being removed from the party
 * drops theirs, and this client leaving, joining another party or the party disbanding drops them all. Turning the
 * setting off drops everything too.
 */
object SafariDowntime {
    private val request = Regex("""^!dt(?:\s+(.+))?$""", RegexOption.IGNORE_CASE)
    private val resume = Regex("""^r$""", RegexOption.IGNORE_CASE)
    private const val NAME = "(?:\\[[^]]+]\\s*)*([A-Za-z0-9_]{1,16})"
    /** One member gone: their request goes with them. */
    private val departed = listOf(
        Regex("^$NAME (?:has left the party|has been removed from the party)[.!]$"),
        Regex("^$NAME was removed from your party because they disconnected[.!]?$"),
        Regex("^Kicked $NAME because they were offline[.!]?$"))
    /** This client is in a different party (or none) now: every request belonged to the old one. */
    private val partyGone = listOf(
        Regex("^You (?:left the party|have been kicked from the party(?: by .+)?)[.!]?$"),
        Regex("^You have joined .+'s party!$"),
        Regex("^(?:The party was disbanded|$NAME has disbanded the party)(?:[.!]| because .+)$"),
        Regex("^You are not (?:currently )?in a party[.!]?$"))
    /** Requester -> reason (may be empty), in the order they asked. */
    private val requests = linkedMapOf<String, String>()
    private var active = false
    /** A hold only stands while the setting is on, so switching it off releases the manager at once. */
    val holding get() = active && ConfigManager.downtimeCommand
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
        if (!ConfigManager.downtimeCommand) { clear(); return null }
        if (partyChanged(SafariRules.strip(text).trim())) return null
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
            if (requests.isEmpty()) active = false
            tell("[SM] ${chat.sender} is back" + if (active) "; still waiting on ${holders()}." else ". Safari Manager released.")
        }
        return null
    }

    /** Party membership notices. Returns whether the line was one, so it isn't read as chat. */
    private fun partyChanged(line: String): Boolean {
        if (partyGone.any { it.matches(line) }) {
            if (requests.isNotEmpty()) tell("[SM] Party changed: downtime requests dropped" + if (active) ". Safari Manager released." else ".")
            clear()
            return true
        }
        val name = departed.firstNotNullOfOrNull { it.matchEntire(line) }?.groupValues?.get(1) ?: return false
        val key = requests.keys.firstOrNull { it.equals(name, true) } ?: return true
        requests.remove(key)
        val released = active && requests.isEmpty()
        if (requests.isEmpty()) active = false
        tell("[SM] $key left the party: their downtime request is dropped" +
            if (released) ". Safari Manager released." else if (active) "; still waiting on ${holders()}." else ".")
        return true
    }

    /** The run is over: banked requests become a hold. Returns the requester's own announcement, if this is them. */
    fun leftSafari(self: String): String? {
        if (!ConfigManager.downtimeCommand) { clear(); return null }
        if (requests.isEmpty() || active) return null
        active = true
        tell("[SM] Safari Manager held for downtime. Say r in party chat when ready (sneak-click to skip).")
        val mine = requests.entries.firstOrNull { it.key.equals(self, true) } ?: return null
        return "${mine.key} has requested downtime" + if (mine.value.isEmpty()) "" else " for reason: ${mine.value}"
    }

    fun onClientTick(client: Minecraft) {
        if (!ConfigManager.downtimeCommand && (active || requests.isNotEmpty())) clear()
        val inSafari = SafariAssist.inSafari
        if (wasInSafari && !inSafari) leftSafari(client.player?.name?.string.orEmpty())?.let(::send)
        wasInSafari = inSafari
    }

    fun holders() = requests.keys.joinToString(", ")
    /** The one-time bypass: a sneak-click on the manager ends the hold for good. */
    fun clear() { active = false; requests.clear() }
    fun reset() { clear(); wasInSafari = false }

    private fun send(message: String) { Minecraft.getInstance().player?.connection?.sendCommand("pc $message") }
    private fun tell(text: String) { Minecraft.getInstance()?.player?.sendSystemMessage(LocalChat.component(text)) }
}
