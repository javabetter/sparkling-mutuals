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
    @Test fun `party PB replies are recognised and full clear replies are unchanged`() {
        assertTrue(PartyCommand(PartyCommandKind.PB_FOREST).matchesResponse("Player's Forest Unique Run PB: 0:44.000 | Party PB: 0:40.000"))
        assertTrue(PartyCommand(PartyCommandKind.PB_FOREST).matchesResponse("Player's Forest Unique Run PB: 0:44.000"))
        val bests = SafariPersonalBests().apply { selectBiomeType(BiomePbType.FULL_CLEAR) }
        assertEquals("Player's Forest Full Clear PB: Not recorded yet", bests.response("Player", "Forest"))
    }
}
