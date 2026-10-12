package net.johnceo.sparklingmutuals

import net.johnceo.sparklingmutuals.history.*
import net.johnceo.sparklingmutuals.safari.SafariRun
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.io.TempDir

class HistoryTest {
    @Test fun `entry and first capture before a tick are saved in the new run`() {
        val session = LiveHistorySession()
        val run = SafariRun(1_000_000)
        session.onTicket(null)
        session.chat(run, "Me entered Critter Safari!\nCAPTURE! You caught a Cavernfish and gained 2x Cavernfish Shard!", "Me") { fail<Unit>("there is no old run to finish") }
        assertEquals(1, session.tally.caught)
        assertEquals(mapOf("Cavernfish" to 2), session.tally.shards)
        assertEquals(1, session.tickets)
        assertSame(run, session.active)
    }
    @Test fun `a new run's first capture finishes the old tally before adding the new capture`() {
        val session = LiveHistorySession()
        val old = SafariRun(1_000_000)
        val next = SafariRun(2_000_000)
        session.onTicket(null)
        session.sync(old) { fail<Unit>("there is no old run to finish") }
        session.chat(old, "CAPTURE! You caught a Cavernfish and gained a Cavernfish Shard!", "Me") { fail<Unit>("the run has not changed") }
        session.splits["Cavern"] = 60_000
        session.onTicket(next)
        var finished = 0
        session.chat(next, "CAPTURE! You caught a Bloodbat and gained a Bloodbat Shard!", "Me") { ended ->
            finished++
            assertSame(old, ended)
            assertEquals(1, session.tally.caught)
            assertEquals(mapOf("Cavernfish" to 1), session.tally.shards)
            assertEquals(mapOf("Cavern" to 60_000L), session.splits)
            assertEquals(1, session.tickets)
        }
        session.sync(next) { fail<Unit>("the old run must finish only once") }
        assertEquals(1, finished)
        assertEquals(1, session.tally.caught)
        assertEquals(mapOf("Bloodbat" to 1), session.tally.shards)
        assertTrue(session.splits.isEmpty())
        assertEquals(1, session.tickets)
    }
    @Test fun `a ticket callback after chat synchronization belongs to the current run`() {
        val session = LiveHistorySession()
        val run = SafariRun(1_000_000)
        session.sync(run) { fail<Unit>("there is no old run to finish") }
        session.onTicket(run)
        assertEquals(1, session.tickets)
        session.sync(null) { }
        session.sync(SafariRun(2_000_000)) { }
        assertEquals(0, session.tickets, "the ticket must not leak into the following run")
    }
    @Test fun `a ticket handed in between runs survives the departure synchronization`() {
        val session = LiveHistorySession()
        session.sync(SafariRun(1_000_000)) { }
        session.onTicket(null)
        session.sync(null) { assertEquals(0, session.tickets, "the new ticket is not part of the old run") }
        session.sync(SafariRun(2_000_000)) { }
        assertEquals(1, session.tickets)
    }
    @Test fun `finishing a live run replaces only its imported duplicate`() {
        val previous = RunRecord(number = 1, start = 1_000_000, length = 50_000, imported = true)
        val duplicate = RunRecord(number = 2, start = 1_100_000, length = 50_000, imported = true)
        val runs = mutableListOf(duplicate, previous)
        val live = RunRecord(start = 1_100_050, length = 50_000)
        assertSame(duplicate, HistoryMerge.removeLiveDuplicate(runs, live))
        assertEquals(listOf(previous), runs)
    }
    @Test fun `finishing a new short run keeps the preceding imported run`() {
        val previous = RunRecord(number = 1, start = 1_000_000, length = 100_000, imported = true)
        val runs = mutableListOf(previous)
        assertNull(HistoryMerge.removeLiveDuplicate(runs, RunRecord(start = 1_100_050, length = 30_000)))
        assertEquals(listOf(previous), runs)
    }
    @Test fun `non-chat lines advance the day before the first Safari chat`() {
        val log = listOf(
            "[23:50:00] [Render thread/INFO]: Starting session",
            "[00:05:00] [Render thread/INFO]: Reloading resources",
            "[00:10:00] [Render thread/INFO]: [CHAT] [NPC] Safari Manager: Looks good to me. Have fun out there!",
            "[00:10:30] [Render thread/INFO]: [CHAT] CAPTURE! You caught a Cavernfish and gained a Cavernfish Shard!",
            "[00:11:00] [Render thread/INFO]: [CHAT] Sending to server mini1A...").joinToString("\n")
        val parser = LogImporter.Parser("Me")
        parser.read(log.reader().buffered(), LocalDate.of(2026, 10, 10))
        assertEquals(LocalDate.of(2026, 10, 11).atTime(0, 10).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), parser.runs.single().start)
        assertEquals(60_000L, parser.runs.single().length)
    }
    @Test fun `a copied latest log preserves its final run`(@TempDir folder: Path) {
        Files.writeString(folder.resolve("latest.log"), listOf(
            "[13:00:00] [Render thread/INFO]: [CHAT] [NPC] Safari Manager: Looks good to me. Have fun out there!",
            "[13:00:30] [Render thread/INFO]: [CHAT] CAPTURE! You caught a Cavernfish and gained a Cavernfish Shard!").joinToString("\n"))
        val result = LogImporter.read(folder, "Me", includeLatest = true, newerThan = -1).join()
        assertEquals(1, result.runs.size)
        assertEquals(30_000L, result.runs.single().length)
    }
    @Test fun `a run's chat gives shards, feathers, macaws and sparklings`() {
        val tally = RunTally()
        listOf("CAPTURE! You caught a Cavernfish and gained 2x Cavernfish Shard!",
            "LOOT SHARE! You received a Macaw Shard from Farmer catching a Macaw!",
            "CAPTURE! You caught a SPARKLING Bloodbat and received a Rainbow Feather and 3x Bloodbat Shard!",
            RunTally.MACAW_BONUS, "SPARKLING! Me caught a SPARKLING Bloodbat!",
            "LOOT SHARE! You received a Rainbow Feather and 1x Treefrog Shard from Farmer catching a SPARKLING Treefrog!")
            .forEach { tally.line(it, "Me") }
        assertEquals(mapOf("Cavernfish" to 2, "Macaw" to 1, "Bloodbat" to 3, "Treefrog" to 1), tally.shards)
        assertEquals(3, tally.feathers)
        assertEquals(1, tally.macaws)
        assertEquals(2, tally.caught); assertEquals(2, tally.lootShares); assertEquals(4, tally.party.size)
        assertEquals(listOf("Me:Bloodbat"), tally.sparklings); assertEquals(1, tally.ownSparklings)
    }
    @Test fun `only the Safari Manager's own ticket lines count`() {
        assertTrue(TicketCounter.isTicket("[NPC] Safari Manager: Looks good to me. Have fun out there!"))
        assertTrue(TicketCounter.isTicket("[NPC] Safari Manager: I already saw your ticket, so you're free to go."))
        assertFalse(TicketCounter.isTicket("Party > Farmer: [NPC] Safari Manager: Looks good to me. Have fun out there!"))
        assertFalse(TicketCounter.isTicket("[NPC] Safari Manager: You already gave me your ticket, bud."))
    }
    @Test fun `old logs rebuild runs from ticket to warp out, with splits from party calls`() {
        val log = listOf(
            "[13:00:00] [Render thread/INFO]: [CHAT] [NPC] Safari Manager: Looks good to me. Have fun out there!",
            "[13:00:30] [Render thread/INFO]: [CHAT] CAPTURE! You caught a Cavernfish and gained a Cavernfish Shard!",
            "[13:01:00] [Render thread/INFO]: [CHAT] Party > [MVP+] Farmer: cavern done",
            "[13:01:05] [Render thread/INFO]: [CHAT] Party > [MVP+] Farmer: need help icy when someones done",
            "[13:01:10] [Render thread/INFO]: [CHAT] SPARKLING! Farmer caught a SPARKLING Flitter!",
            "[13:02:00] [Render thread/INFO]: [CHAT] Sending to server mini143BE...",
            "[13:03:00] [Render thread/INFO]: [CHAT] [NPC] Safari Manager: You already gave me your ticket, bud.",
            "[13:04:00] [Render thread/INFO]: [CHAT] Sending to server mini1A...").joinToString("\n")
        val parser = LogImporter.Parser("Me")
        parser.read(log.reader().buffered(), LocalDate.of(2026, 10, 10))
        assertEquals(1, parser.runs.size, "a run with nothing caught isn't kept")
        val run = parser.runs[0]
        assertEquals(120_000L, run.length)
        assertEquals(1, run.tickets)
        assertEquals(mapOf("Cavern" to 60_000L), run.splits)
        assertEquals(listOf("Farmer:Flitter"), run.sparklings)
        assertTrue(run.imported)
        assertEquals(1, parser.sparklings.size); assertEquals(1, parser.sparklings[0].tickets)
    }
    @Test fun `reimporting short runs close together matches each saved run once`() {
        val first = RunRecord(start = 1_000_000, imported = true)
        val second = RunRecord(start = 1_100_000, imported = true)
        val runs = mutableListOf(second, first)
        val added = HistoryMerge.merge(runs, listOf(RunRecord(start = 1_000_000, caught = 3, imported = true),
            RunRecord(start = 1_100_000, caught = 4, imported = true)))
        assertEquals(0, added)
        assertEquals(listOf(4, 3), runs.map { it.caught })
        assertEquals(listOf(2, 1), runs.map { it.number })
    }
    @Test fun `a run still open at the end of latest log is left for live recording`() {
        val log = listOf(
            "[13:00:00] [Render thread/INFO]: [CHAT] [NPC] Safari Manager: Looks good to me. Have fun out there!",
            "[13:00:30] [Render thread/INFO]: [CHAT] CAPTURE! You caught a Cavernfish and gained a Cavernfish Shard!").joinToString("\n")
        val live = LogImporter.Parser("Me").apply { read(log.reader().buffered(), LocalDate.of(2026, 10, 10), closeAtEnd = false) }
        assertTrue(live.runs.isEmpty())
        val archived = LogImporter.Parser("Me").apply { read(log.reader().buffered(), LocalDate.of(2026, 10, 10)) }
        assertEquals(1, archived.runs.size)
    }
    @Test fun `latest log's start day accounts for midnight`() {
        fun t(h: Int, m: Int) = java.time.LocalTime.of(h, m)
        val modified = java.time.LocalDateTime.of(2026, 10, 11, 1, 0)
        assertEquals(LocalDate.of(2026, 10, 10), LogImporter.startDay(listOf(t(23, 0), t(23, 59), t(0, 30)), modified))
        assertEquals(LocalDate.of(2026, 10, 11), LogImporter.startDay(listOf(t(0, 10), t(0, 50)), modified))
        // Last line just before midnight, file flushed just after.
        assertEquals(LocalDate.of(2026, 10, 10), LogImporter.startDay(listOf(t(22, 0), t(23, 59)), java.time.LocalDateTime.of(2026, 10, 11, 0, 0, 5)))
    }
    @Test fun `a contest completion keeps its own window across boundaries and days`() {
        val a = net.johnceo.sparklingmutuals.contest.ContestTracker.absoluteWindow(java.time.LocalDateTime.of(2026, 10, 10, 12, 34, 50))
        val b = net.johnceo.sparklingmutuals.contest.ContestTracker.absoluteWindow(java.time.LocalDateTime.of(2026, 10, 10, 12, 35, 5))
        val tomorrow = net.johnceo.sparklingmutuals.contest.ContestTracker.absoluteWindow(java.time.LocalDateTime.of(2026, 10, 11, 12, 34, 50))
        assertEquals(1, b - a)
        assertEquals(72, tomorrow - a)
    }
    @Test fun `imports skip runs already saved, refresh earlier imports and renumber oldest first`() {
        val live = RunRecord(start = 1_000_000, caught = 5)
        val oldImport = RunRecord(start = 5_000_000, caught = 1, imported = true)
        val runs = mutableListOf(oldImport, live)
        val added = HistoryMerge.merge(runs, listOf(RunRecord(start = 1_060_000, imported = true),
            RunRecord(start = 5_030_000, caught = 9, imported = true), RunRecord(start = 100_000, imported = true)))
        assertEquals(1, added)
        assertSame(live, runs.first { it.start == 1_000_000L })
        assertEquals(9, runs.first { it.start == 5_030_000L }.caught)
        assertEquals(listOf(3, 2, 1), runs.map { it.number })
    }
    @Test fun `achievement definitions are unique and teammates' broadcasts aren't your Emerald Dye`() {
        val ids = Achievements.definitions.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(Achievements.ownDrop("RARE DROP! Emerald Dye", "Me"))
        assertTrue(Achievements.ownDrop("WOW! You found Emerald Dye #12!", "Me"))
        assertFalse(Achievements.ownDrop("WOW! [MVP+] Farmer found Emerald Dye #12!", "Me"))
    }
}
