package net.johnceo.sparklingmutuals

import net.johnceo.sparklingmutuals.commands.PartyCommand
import net.johnceo.sparklingmutuals.commands.PartyCommandKind
import net.johnceo.sparklingmutuals.safari.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SafariUniquePartyPbTest {
    private fun catchAll(run: SafariRun, biome: SafariBiome, personal: (SafariCritter) -> Boolean) =
        biome.critters.forEach { run.record(SafariCatch(it, personal(it))) }

    @Test fun `teammates' catches complete the party unique PB but not the personal one`() {
        val bests = SafariPersonalBests().apply { selectBiomeType(BiomePbType.UNIQUE) }
        val run = SafariRun(0)
        catchAll(run, SafariBiome.ICY) { it.name == "Tepid" }
        assertNull(bests.recordUnique(SafariBiome.ICY, run, 90_000))
        assertEquals("[SM] New Icy Unique Run Party PB: 1:30.000!", bests.recordPartyUnique(SafariBiome.ICY, run, 90_000))
        assertNull(bests.recordPartyUnique(SafariBiome.ICY, run, 80_000), "once per run")
        assertEquals("Player's Icy Unique Run PB: Not recorded yet | Party PB: 1:30.000", bests.response("Player", "Icy"))
    }
    @Test fun `only a faster party time replaces the record and a missing critter blocks it`() {
        val bests = SafariPersonalBests().apply { selectBiomeType(BiomePbType.UNIQUE) }
        val first = SafariRun(0).also { catchAll(it, SafariBiome.HAUNTED) { false } }
        assertNotNull(bests.recordPartyUnique(SafariBiome.HAUNTED, first, 100_000))
        val slower = SafariRun(0).also { catchAll(it, SafariBiome.HAUNTED) { false } }
        assertNull(bests.recordPartyUnique(SafariBiome.HAUNTED, slower, 120_000))
        val partial = SafariRun(0).also { run -> SafariBiome.HAUNTED.critters.drop(1).forEach { run.record(SafariCatch(it, false)) } }
        assertNull(bests.recordPartyUnique(SafariBiome.HAUNTED, partial, 50_000))
        assertEquals(100_000L, bests.partyTime("Haunted"))
    }
    @Test fun `a scan proving the last critter absent completes the party PB`() {
        val bests = SafariPersonalBests().apply { selectBiomeType(BiomePbType.UNIQUE) }
        val run = SafariRun(0)
        run.visitBiome(SafariBiome.CAVERN)
        SafariBiome.CAVERN.critters.filter { it.name != "Snoozle" }.forEach { run.record(SafariCatch(it, false)) }
        // The last catch arrives before the walls are checked: not done yet.
        assertNull(bests.recordPartyUnique(SafariBiome.CAVERN, run, 60_000))
        // A later scan finds every wall checked and no Snoozle, with no further catch.
        run.updateCaptureEvidence(SafariBiome.CAVERN, emptySet(), allWallsChecked = true)
        assertEquals("[SM] New Cavern Unique Run Party PB: 1:10.000!", bests.recordPartyUnique(SafariBiome.CAVERN, run, 70_000))
    }
    @Test fun `a teammate's catch before you reach a biome does not void its party PB`() {
        val bests = SafariPersonalBests().apply { selectBiomeType(BiomePbType.UNIQUE) }
        val run = SafariRun(0)
        run.visitBiome(SafariBiome.FOREST)
        val icy = SafariBiome.ICY.critters
        run.record(SafariCatch(icy.first(), false))
        run.visitBiome(SafariBiome.ICY)
        assertTrue(run.inherited(SafariBiome.ICY), "the run's own flag is unchanged")
        icy.drop(1).forEach { run.record(SafariCatch(it, true)) }
        assertNotNull(bests.recordPartyUnique(SafariBiome.ICY, run, 200_000))
    }
    @Test fun `party PB replies are recognised and full clear replies are unchanged`() {
        assertTrue(PartyCommand(PartyCommandKind.PB_FOREST).matchesResponse("Player's Forest Unique Run PB: 0:44.000 | Party PB: 0:40.000"))
        assertTrue(PartyCommand(PartyCommandKind.PB_FOREST).matchesResponse("Player's Forest Unique Run PB: 0:44.000"))
        val bests = SafariPersonalBests().apply { selectBiomeType(BiomePbType.FULL_CLEAR) }
        assertEquals("Player's Forest Full Clear PB: Not recorded yet", bests.response("Player", "Forest"))
    }
}
