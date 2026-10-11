package net.johnceo.sparklingmutuals.history

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.johnceo.sparklingmutuals.api.HypixelApi
import net.johnceo.sparklingmutuals.commands.SafariLookup
import net.johnceo.sparklingmutuals.safari.SafariMessages
import net.johnceo.sparklingmutuals.safari.SafariRoster
import net.minecraft.client.Minecraft
import java.util.concurrent.Executors

/**
 * Every sparkling caught while this client was watching (yours and party members' announced in chat), with the
 * tickets the party used since the previous one. Saved to `sparkling-mutuals-sparklings.json`.
 */
object SparklingLog {
    class Data(var entries: MutableList<SparklingEntry> = mutableListOf(), var ticketsSinceSparkling: Int = 0)
    private var data = Data()
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "Sparkling Mutuals sparkling log").apply { isDaemon = true } }
    val entries: List<SparklingEntry> get() = data.entries
    /** Tickets the party has used since anyone's last sparkling. */
    val ticketsSinceSparkling get() = data.ticketsSinceSparkling

    fun register() {
        data = HistoryStore.load("sparklings", Data::class.java, ::Data).also { d -> d.entries.sortByDescending { it.at } }
        ClientReceiveMessageEvents.GAME.register { message, overlay ->
            if (!overlay) Minecraft.getInstance().execute { SafariMessages.lines(message.string).forEach(::chat) }
        }
    }

    private fun chat(text: String) {
        val match = RunTally.sparkling.matchEntire(text) ?: return
        val player = match.groupValues[1]
        val critter = SafariRoster.named(match.groupValues[2].trim())?.name ?: match.groupValues[2].trim()
        val entry = SparklingEntry(System.currentTimeMillis(), player, critter, tickets = data.ticketsSinceSparkling)
        data.ticketsSinceSparkling = 0
        data.entries.add(0, entry)
        save()
        val client = Minecraft.getInstance()
        val own = player.equals(client.player?.name?.string, true)
        Achievements.onSparkling(entry, own)
        markUnique(entry, own)
    }

    /**
     * Unique means the player hadn't discovered it before. The discoveries lookup is cached for a minute, so it usually
     * predates the catch; a fresh one can race the server recording it, in which case the catch isn't flagged.
     */
    private fun markUnique(entry: SparklingEntry, own: Boolean) {
        val client = Minecraft.getInstance()
        val earlier = data.entries.any { it !== entry && it.player.equals(entry.player, true) && it.critter.equals(entry.critter, true) }
        if (earlier) return
        executor.submit {
            val known = runCatching { SafariLookup.discoveries(HypixelApi.getPlayerUuid(entry.player)) }.getOrNull() ?: return@submit
            val key = entry.critter.uppercase(java.util.Locale.ROOT).replace(' ', '_')
            if (key !in known) client.execute { entry.unique = true; save(); Achievements.onUnique(own) }
        }
    }

    fun onTicket() { data.ticketsSinceSparkling++; save() }

    /** Adds catches found elsewhere (old logs), skipping any already here. Returns how many were added. */
    fun importEntries(found: List<SparklingEntry>): Int {
        val added = found.filter { candidate -> data.entries.none {
            it.player.equals(candidate.player, true) && it.critter.equals(candidate.critter, true) && kotlin.math.abs(it.at - candidate.at) <= 300_000
        } }
        if (added.isEmpty()) return 0
        data.entries.addAll(added)
        data.entries.sortByDescending { it.at }
        save()
        return added.size
    }

    /** Which catch of this critter an entry was, counting everyone's. */
    fun numberOf(entry: SparklingEntry) = data.entries.count { it.critter.equals(entry.critter, true) && it.at <= entry.at }

    fun save() = HistoryStore.save("sparklings", data)
}
