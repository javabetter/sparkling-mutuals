package net.johnceo.sparklingmutuals.history

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.johnceo.sparklingmutuals.config.ConfigManager
import net.johnceo.sparklingmutuals.party.PartyManager
import net.johnceo.sparklingmutuals.safari.SafariBiome
import net.johnceo.sparklingmutuals.safari.SafariFullClear
import net.johnceo.sparklingmutuals.safari.SafariMessages
import net.johnceo.sparklingmutuals.safari.SafariRun
import net.johnceo.sparklingmutuals.safari.SafariSparklingMode
import net.johnceo.sparklingmutuals.safari.SafariTracking
import net.minecraft.client.Minecraft

/**
 * Every Safari run, saved. A run is whatever the tracker's ledger calls one (arriving to leaving); when it closes it
 * is written to `sparkling-mutuals-runs.json` with its captures, shards, feathers, sparklings and biome splits.
 */
object RunHistory {
    class Data(var nextNumber: Int = 1, var runs: MutableList<RunRecord> = mutableListOf())
    private var data = Data()
    private var active: SafariRun? = null
    private var tally = RunTally()
    private val splits = linkedMapOf<String, Long>()
    private var tickets = 0
    /** Tickets are handed in at the entrance, before the run starts; held until it does. */
    private var pendingTickets = 0
    private var ticks = 0

    val runs: List<RunRecord> get() = data.runs

    fun register() {
        data = HistoryStore.load("runs", Data::class.java, ::Data).also { d -> d.runs.sortByDescending { it.start } }
        ClientReceiveMessageEvents.GAME.register { message, overlay ->
            if (!overlay) Minecraft.getInstance().execute { chat(message.string) }
        }
    }

    private fun chat(raw: String) {
        if (active == null) return
        val self = Minecraft.getInstance().player?.name?.string.orEmpty()
        SafariMessages.lines(raw).forEach { tally.line(it, self) }
    }

    fun onTicket() { pendingTickets++ }

    fun tick(client: Minecraft) {
        val run = SafariTracking.ledger.current
        if (run !== active) {
            active?.let { finish(it, client) }
            active = run
            tally = RunTally(); splits.clear()
            tickets = pendingTickets; pendingTickets = 0
        }
        val current = active ?: return
        if (++ticks % 10 != 0) return
        val now = System.currentTimeMillis()
        val party = SafariSparklingMode.state
        for (biome in SafariBiome.entries) if (biome.label !in splits &&
            current.completed(SafariFullClear.mode, biome.critters, party) == biome.critters.size)
            splits[biome.label] = now - current.startedAt
    }

    private fun finish(run: SafariRun, client: Minecraft) {
        if (!ConfigManager.runHistory || tally.party.isEmpty()) return
        val record = RunRecord(start = run.startedAt, length = run.elapsed(System.currentTimeMillis()),
            mode = SafariFullClear.mode.name, party = tally.party.size, you = tally.catches.size,
            caught = tally.caught, lootShares = tally.lootShares,
            partySize = client.player?.uuid?.toString()?.let(PartyManager::withLocal)?.size ?: 1,
            tickets = tickets, shards = LinkedHashMap(tally.shards), catches = LinkedHashMap(tally.catches),
            feathers = tally.feathers, sparklings = tally.sparklings.toMutableList(), ownSparklings = tally.ownSparklings,
            macaws = tally.macaws, splits = LinkedHashMap(splits))
        // A manual import during this run may have read it from latest.log already; the live record replaces that.
        val imported = data.runs.filter { it.imported && kotlin.math.abs(it.start - record.start) <= HistoryMerge.SAME_RUN_MILLIS }
        data.runs.removeAll(imported)
        record.number = imported.minOfOrNull { it.number } ?: data.nextNumber++
        data.runs.add(0, record)
        save()
        Achievements.onRunEnd(record)
    }

    /**
     * Adds runs rebuilt from old logs. A run already saved (starting within two minutes) is kept as it is, unless it
     * came from an earlier import, which this reading replaces. Everything is then numbered oldest first.
     */
    fun importRuns(found: List<RunRecord>): Int {
        val added = HistoryMerge.merge(data.runs, found)
        data.nextNumber = data.runs.size + 1
        save()
        return added
    }

    fun save() = HistoryStore.save("runs", data)
}

/** The merge behind imports, separate so it can be tested. */
object HistoryMerge {
    const val SAME_RUN_MILLIS = 120_000L
    /**
     * Merges [found] into [runs] (newest first afterwards), renumbering oldest first; returns how many were new.
     * Each saved run matches at most one found run, the closest in time; runs found in this batch never match
     * each other, so two short runs close together both survive.
     */
    fun merge(runs: MutableList<RunRecord>, found: List<RunRecord>): Int {
        val unmatched = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<RunRecord, Boolean>()).apply { addAll(runs) }
        var added = 0
        for (run in found) {
            val existing = unmatched.filter { kotlin.math.abs(it.start - run.start) <= SAME_RUN_MILLIS }
                .minByOrNull { kotlin.math.abs(it.start - run.start) }
            if (existing == null) { runs += run; added++; continue }
            unmatched.remove(existing)
            if (existing.imported) {
                val index = runs.indexOfFirst { it === existing }
                run.number = existing.number
                runs[index] = run
            }
        }
        runs.sortBy { it.start }
        runs.forEachIndexed { index, run -> run.number = index + 1 }
        runs.sortByDescending { it.start }
        return added
    }
}
