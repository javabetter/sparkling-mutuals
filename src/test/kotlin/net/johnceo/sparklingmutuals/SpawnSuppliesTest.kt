package net.johnceo.sparklingmutuals

import net.johnceo.sparklingmutuals.safari.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SpawnSuppliesTest {
    @Test fun `sparkling birds wait for nine foods plus starting inventory and count macaw pairs once`() {
        for (extra in 0..2) {
            val birds = SafariBirdLedger()
            repeat(extra) { birds.pickup("FLOOR DROP! Yogi Berry", null) }
            birds.startingInventory(listOf("§aYogi Berry" to extra, "Yogi Berry Shard" to 9), 500)
            birds.startingInventory(listOf("Yogi Berry" to extra), 2000)
            birds.startingInventory(listOf("Yogi Berry" to 30), 3001)
            assertEquals(9 + extra, birds.sparklingTarget)
            repeat(8 + extra) { birds.spawn("Two Macaws were attracted to the Birdfeeder!") }
            assertFalse(birds.sparklingSpawned)
            birds.spawn("A Bluebird was attracted to the Birdfeeder!")
            assertTrue(birds.sparklingSpawned)
            assertFalse(birds.allSpawned) // Full Clear still needs pickup/capture evidence.
        }
    }
    @Test fun `early floor pickups and repeated inventory scans do not inflate starting food`() {
        val run = SafariRun(1000)
        run.birds.pickup("FLOOR DROP! Bag of Seeds", SafariBiome.FOREST)
        run.inventoryEvidence(listOf("Bag of Seeds" to 2, "Wriggleworm" to 1), 4000)
        run.inventoryEvidence(listOf("Bag of Seeds" to 2, "Wriggleworm" to 1), 4000)
        run.inventoryEvidence(listOf("Bag of Seeds" to 20), 4001)
        assertEquals(11, run.birds.sparklingTarget)
        val fresh = SafariRun(5000)
        fresh.inventoryEvidence(listOf("Yogi Berry" to 2), 4999)
        fresh.inventoryEvidence(emptyList(), 8001)
        assertEquals(9, fresh.birds.sparklingTarget)
    }
    @Test fun `full clear keeps forest and haunted supplies despite party discoveries and incense`() {
        val state = FloorDropState()
        state.inventory(listOf("Soothing Incense" to 4))
        for (biome in listOf(SafariBiome.FOREST, SafariBiome.HAUNTED)) {
            assertTrue(state.enabled(biome, true, true, forestFoodComplete = true,
                partyBirdsComplete = true, partyGimmiegoldComplete = true, doomCaught = true))
            assertFalse(state.enabled(biome, false, true, forestFoodComplete = true,
                partyBirdsComplete = true, partyGimmiegoldComplete = true, doomCaught = true))
            assertFalse(state.enabled(biome, true, false))
        }
    }
    @Test fun `full clear nests ignore inherited claims and all structure helpers stay needed`() {
        val run = SafariRun(0)
        run.partyFullClear("Party > Friend: fd")
        val party = PartySparklingState()
        assertTrue(run.partyNestsHandled)
        assertTrue(SafariHelperRules.showNests(SafariMode.FULL_CLEAR, run, party))
        assertFalse(SafariHelperRules.showNests(SafariMode.SPARKLING, run, party))
        for (name in SafariHelperRules.species) assertTrue(SafariHelperRules.needed(name, SafariMode.FULL_CLEAR, run, party))
    }
}
