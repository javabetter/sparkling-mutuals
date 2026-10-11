package net.johnceo.sparklingmutuals

import net.johnceo.sparklingmutuals.config.*
import net.johnceo.sparklingmutuals.safari.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class SafariProfitableUniqueTest {
    @TempDir lateinit var dir: Path
    @Test fun `every Unique critter hides after first capture even with legacy profitable preference`() {
        for (critter in SafariRoster.all) {
            val run = SafariRun(0)
            assertTrue(SafariEspRules.neededForRun(critter.name, false, run, false, false))
            run.record(SafariCatch(critter, false))
            assertFalse(SafariEspRules.neededForRun(critter.name, false, run, false, true))
            assertFalse(SafariEspRules.neededForRun(critter.name, false, run, false, false))
            assertTrue(SafariEspRules.neededForRun(critter.name, false, run, true, false))
        }
    }
    @Test fun `profitable option defaults on and Modes subsetting persists without changing full clear`() {
        ConfigManager.init(dir)
        assertTrue(ConfigManager.profitableShardEsp)
        val settings = SafariSettings()
        settings.modes.unique.profitable = false
        settings.apply(); ConfigManager.init(dir)
        assertFalse(ConfigManager.profitableShardEsp)
        SafariFullClear.setEnabled(true); SafariFullClear.setEnabled(false)
        assertTrue(SafariEspConfig.mobs.getValue("Chuckwalla").enabled)
        assertEquals("[SM] Unique run mode on.", SafariFullClear.modeMessage())
        SafariFullClear.setEnabled(true)
        assertEquals("[SM] Full clear mode on.", SafariFullClear.modeMessage())
    }
    @Test fun `profitable highlights survive label and motion gaps only for current entities`() {
        for (sparkling in listOf(false, true)) for (name in listOf("Hideonfloor", "Hideonwall", "Chuckwalla", "Fluffling", "Mantis Shrimp")) {
            assertTrue(SafariEspRules.renderCurrent(name, sparkling, true, false, false, true))
            assertFalse(SafariEspRules.renderCurrent(name, sparkling, false, true, true, true))
        }
        assertFalse(SafariEspRules.renderCurrent("Hideonwall", true, true, false, false, false))
        assertFalse(SafariEspRules.renderCurrent("Flitter", true, true, false, false, true))
    }
    @Test fun `profitable Sparkling toggle preserves all five species after catches and checks`() {
        val party = PartySparklingState().apply {
            select(setOf("me")); accept(setOf("me"), mapOf("me" to SafariRoster.all.map { it.name }.toSet()))
        }
        for (name in listOf("Hideonfloor", "Hideonwall", "Chuckwalla", "Fluffling", "Mantis Shrimp")) {
            val run = SafariRun(0); val id = java.util.UUID.randomUUID()
            run.record(SafariCatch(SafariRoster.named(name)!!))
            assertTrue(SafariEspRules.neededForSparkling(name, false, party, true, run, id, 0))
            assertTrue(SafariEspRules.neededForSparkling(name, false, party, true, run, id, 60000))
            assertFalse(SafariEspRules.neededForSparkling(name, false, party, false, run, id, 60000))
        }
    }
    @Test fun `nine Forest pickups hide unique drops for run while starting food alone cannot`() {
        val run = SafariRun(0); val floor = FloorDropState()
        repeat(2) { run.recordBirdFood("FLOOR DROP! Bag of Seeds", null) }
        assertTrue(floor.enabled(SafariBiome.FOREST, false, true, run.birdFoodsComplete))
        repeat(3) { run.recordBirdFood("FLOOR DROP! Bag of Seeds"); run.recordBirdFood("FLOOR DROP! Wriggleworm") }
        repeat(2) { run.recordBirdFood("FLOOR DROP! Yogi Berry") }
        assertTrue(floor.enabled(SafariBiome.FOREST, false, true, run.birdFoodsComplete))
        run.recordBirdFood("FLOOR DROP! Yogi Berry")
        assertFalse(floor.enabled(SafariBiome.FOREST, false, true, run.birdFoodsComplete))
        floor.visit(null); floor.visit(SafariBiome.FOREST)
        assertFalse(floor.enabled(SafariBiome.FOREST, false, true, run.birdFoodsComplete))
        assertTrue(floor.enabled(SafariBiome.FOREST, true, true, run.birdFoodsComplete))
        assertTrue(floor.enabled(SafariBiome.HAUNTED, false, true, run.birdFoodsComplete))
        assertTrue(floor.enabled(SafariBiome.FOREST, false, true, SafariRun(1000).birdFoodsComplete))
    }
}
