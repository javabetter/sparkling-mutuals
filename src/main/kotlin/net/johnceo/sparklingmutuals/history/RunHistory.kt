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
    private val session = LiveHistorySession()
    private val tally get() = session.tally
    private val splits get() = session.splits
    private val tickets get() = session.tickets
    private var ticks = 0

    val runs: List<RunRecord> get() = data.runs

    fun register() {
        data = HistoryStore.load("runs", Data::class.java, ::Data).also { d -> d.runs.sortByDescending { it.start } }
        BazaarPrices.ensureFresh()
        ClientReceiveMessageEvents.GAME.register { message, overlay ->
            if (!overlay) Minecraft.getInstance().execute { chat(message.string) }
        }
    }

    private fun chat(raw: String) {
        val client = Minecraft.getInstance()
        session.chat(SafariTracking.ledger.current, raw, client.player?.name?.string.orEmpty()) { finish(it, client) }
    }

    fun onTicket() = session.onTicket(SafariTracking.ledger.current)

    fun tick(client: Minecraft) {
        session.sync(SafariTracking.ledger.current) { finish(it, client) }
        val current = session.active ?: return
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
        val imported = HistoryMerge.removeLiveDuplicate(data.runs, record)
        record.number = imported?.number ?: data.nextNumber++
        record.coins = BazaarPrices.value(record.shards, record.feathers)
        data.runs.add(0, record)
        save()
        Achievements.onRunEnd(record)
        OwnStats.refreshSoon() // the bestiary now has this run too
    }

    /**
     * Adds runs rebuilt from old logs. A run already saved (starting within two minutes) is kept as it is, unless it
     * came from an earlier import, which this reading replaces. Everything is then numbered oldest first.
     */
    fun importRuns(found: List<RunRecord>): Int {
        found.forEach { it.coins = BazaarPrices.value(it.shards, it.feathers) }
        val added = HistoryMerge.merge(data.runs, found)
        data.nextNumber = data.runs.size + 1
        save()
        return added
    }

    /** Fills in coins for runs saved before the prices had loaded. */
    fun fillMissingCoins() {
        val missing = data.runs.filter { it.coins < 0 }
        missing.forEach { it.coins = BazaarPrices.value(it.shards, it.feathers) }
        if (missing.any { it.coins >= 0 }) save()
    }

    fun save() = HistoryStore.save("runs", data)
}

/** Client-thread tally state shared by chat callbacks and ticks. Finish observes the old state before it resets. */
internal class LiveHistorySession {
    var active: SafariRun? = null
        private set
    var tally = RunTally()
        private set
    val splits = linkedMapOf<String, Long>()
    var tickets = 0
        private set
    private var pendingTickets = 0

    /** A later ticket listener may run after chat has already synchronized the new run. */
    fun onTicket(run: SafariRun?) {
        if (run != null && run === active) tickets++ else pendingTickets++
    }

    fun sync(run: SafariRun?, finish: (SafariRun) -> Unit) {
        if (run === active) return
        active?.let(finish)
        active = run
        tally = RunTally(); splits.clear()
        tickets = if (run == null) 0 else pendingTickets
        if (run != null) pendingTickets = 0
    }

    fun chat(run: SafariRun?, raw: String, self: String, finish: (SafariRun) -> Unit) {
        sync(run, finish)
        if (active == null) return
        SafariMessages.lines(raw).forEach { tally.line(it, self) }
    }
}

/** The merge behind imports, separate so it can be tested. */
object HistoryMerge {
    const val SAME_RUN_MILLIS = 120_000L
    /** Replace one overlapping import of this run; nearby, already-finished runs remain separate. */
    fun removeLiveDuplicate(runs: MutableList<RunRecord>, live: RunRecord): RunRecord? {
        val imported = runs.filter { it.imported && kotlin.math.abs(it.start - live.start) <= SAME_RUN_MILLIS &&
            (Math.floorDiv(it.start, 1000L) == Math.floorDiv(live.start, 1000L) ||
                it.start < live.start + live.length && live.start < it.start + it.length) }
            .minByOrNull { kotlin.math.abs(it.start - live.start) }
        if (imported != null) runs.remove(imported)
        return imported
    }
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
