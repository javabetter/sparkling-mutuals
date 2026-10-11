package net.johnceo.sparklingmutuals

import net.johnceo.sparklingmutuals.config.*
import net.johnceo.sparklingmutuals.safari.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class SafariSparklingModeTest {
    @TempDir lateinit var dir: Path
    private val all = SafariRoster.all.map { it.name.uppercase().replace(' ', '_') }.toSet()
    @Test fun `party union includes local needs and never hides from an incomplete roster`() {
        val state = PartySparklingState()
        state.select(setOf("me", "friend"))
        assertTrue(state.needs("Driftling"))
        assertFalse(state.everyoneHas("Gemzie"))
        assertFalse(state.accept(setOf("me", "friend"), mapOf("me" to all)))
        assertTrue(state.needs("Driftling"))
        assertTrue(state.accept(setOf("me", "friend"), mapOf("me" to all - "DRIFTLING", "friend" to all - "GEMZIE")))
        assertTrue(state.needs("Driftling"))
        assertTrue(state.needs("Gemzie"))
        assertFalse(state.needs("Foxtrot"))
        assertTrue(state.everyoneHas("Gimmiegold"))
        assertTrue(state.everyoneHas("Bluebird", "Parakeet", "Macaw"))
        // A failed refresh preserves the last complete result, while a new party invalidates it.
        assertFalse(state.accept(setOf("me", "friend"), mapOf("me" to all)))
        assertFalse(state.needs("Foxtrot"))
        state.select(setOf("me", "newFriend"))
        assertTrue(state.needs("Foxtrot"))
        assertFalse(state.accept(setOf("me", "friend"), mapOf("me" to all, "friend" to all)))
        assertFalse(state.everyoneHas("Gemzie"))
    }
    @Test fun `sparkling ESP stays through catches with a separate optional profitable exception`() {
        val state = PartySparklingState()
        state.select(setOf("me"))
        state.accept(setOf("me"), mapOf("me" to all - "DRIFTLING" - "MANTIS_SHRIMP"))
        val run = SafariRun(0)
        run.record(SafariCatch(SafariRoster.named("Driftling")!!, false))
        assertTrue(SafariEspRules.neededForSparkling("Driftling", false, state, false))
        assertFalse(SafariEspRules.neededForSparkling("Foxtrot", false, state, true))
        assertTrue(SafariEspRules.neededForSparkling("Chuckwalla", false, state, true))
        assertFalse(SafariEspRules.neededForSparkling("Chuckwalla", false, state, false))
        assertTrue(SafariEspRules.neededForSparkling("Mantis Shrimp", false, state, false))
        assertFalse(SafariEspRules.neededForSparkling("Rockmite", true, state, false))
    }
    @Test fun `sparkling selection persists and modes are mutually exclusive without resetting captures`() {
        ConfigManager.init(dir)
        SafariFullClear.select(SafariMode.SPARKLING)
        assertTrue(ConfigManager.sparklingMode)
        assertFalse(ConfigManager.fullClearMode)
        assertTrue(SafariEspConfig.mobs.values.all { it.enabled })
        assertEquals("[SM] Sparkling mode on.", SafariFullClear.modeMessage())
        ConfigManager.sparklingProfitableShardEsp = false
        ConfigManager.save(); ConfigManager.init(dir)
        assertEquals(SafariMode.SPARKLING, SafariFullClear.mode)
        assertFalse(ConfigManager.sparklingProfitableShardEsp)
        SafariFullClear.setEnabled(true)
        assertFalse(ConfigManager.sparklingMode)
        SafariFullClear.select(SafariMode.SPARKLING)
        SafariFullClear.setEnabled(false)
        assertEquals(SafariMode.UNIQUE, SafariFullClear.mode)
    }
    @Test fun `Modes settings switch presets once and keep independent profitable toggles`() {
        ConfigManager.init(dir)
        val settings = SafariSettings()
        settings.modes.sparkling.enabled = true
        settings.modes.sparkling.profitable = false
        settings.apply(); settings.apply()
        assertTrue(ConfigManager.sparklingMode)
        assertFalse(settings.modes.unique.enabled)
        assertFalse(ConfigManager.sparklingProfitableShardEsp)
        assertTrue(ConfigManager.profitableShardEsp)
        assertTrue(SafariEspConfig.mobs.values.all { it.enabled })
        settings.esp.forest = false
        settings.apply()
        assertFalse(SafariEspConfig.groups.getValue("forest").onlyInBiome)
        settings.modes.fullClear.enabled = true
        settings.apply(); settings.apply()
        assertTrue(ConfigManager.fullClearMode)
        assertFalse(ConfigManager.sparklingMode)
        assertFalse(settings.modes.sparkling.enabled)
        settings.modes.unique.enabled = true
        settings.apply()
        assertEquals(SafariMode.UNIQUE, SafariFullClear.mode)
    }
    @Test fun `sparkling command help and config categories are discoverable`() {
        val names = SafariSettings::class.java.fields.mapNotNull { it.getAnnotation(io.github.notenoughupdates.moulconfig.annotations.Category::class.java)?.name }
        assertTrue("Modes" in names)
        assertTrue(net.johnceo.sparklingmutuals.commands.CommandHelp.localLines().any { it.startsWith("§6/sparkle ") })
        assertFalse(net.johnceo.sparklingmutuals.commands.CommandHelp.partyReply().contains("/sparkle"))
    }
    @Test fun `loading conflicting legacy flags prefers sparkling mode without losing full clear records`() {
        java.util.Properties().apply {
            setProperty("fullClearMode", "true")
            setProperty("sparklingMode", "true")
        }.also { properties -> java.nio.file.Files.newOutputStream(dir.resolve("sparkling-mutuals.properties")).use { properties.store(it, "test") } }
        ConfigManager.init(dir)
        assertEquals(SafariMode.SPARKLING, SafariFullClear.mode)
        assertFalse(ConfigManager.fullClearMode)
        assertTrue(ConfigManager.timesaveOnly)
        SafariFullClear.select(SafariMode.FULL_CLEAR)
        ConfigManager.init(dir)
        assertEquals(SafariMode.FULL_CLEAR, SafariFullClear.mode)
    }
    @Test fun `full clear keeps spawning supplies while discovery gates apply to other modes`() {
        val floor = FloorDropState()
        for (full in listOf(false, true)) {
            assertEquals(full, floor.enabled(SafariBiome.FOREST, full, true, partyBirdsComplete = true))
            assertEquals(full, floor.enabled(SafariBiome.CAVERN, full, true, partyGemzieComplete = true))
            assertEquals(full, floor.enabled(SafariBiome.CAVERN, full, true, gemzieCaught = true))
            assertTrue(floor.enabled(SafariBiome.HAUNTED, full, true, doomCaught = true))
            assertEquals(full, floor.enabled(SafariBiome.HAUNTED, full, true, partyGimmiegoldComplete = true, doomCaught = true))
        }
        floor.visit(SafariBiome.CAVERN); floor.force(true)
        assertTrue(floor.enabled(SafariBiome.CAVERN, false, true, gemzieCaught = true))
        floor.visit(null); floor.visit(SafariBiome.CAVERN)
        assertFalse(floor.enabled(SafariBiome.CAVERN, false, true, gemzieCaught = true))
    }
    @Test fun `incense includes starting drops and latches four held even after consumption`() {
        val floor = FloorDropState()
        repeat(2) { floor.record("FLOOR DROP! Soothing Incense", null) }
        floor.record("FLOOR DROP! Soothing Incense", SafariBiome.HAUNTED)
        assertTrue(floor.enabled(SafariBiome.HAUNTED, false, true, partyGimmiegoldComplete = true))
        floor.record("FLOOR DROP! Soothing Incense", SafariBiome.HAUNTED)
        assertFalse(floor.enabled(SafariBiome.HAUNTED, false, true, partyGimmiegoldComplete = true))
        assertTrue(floor.enabled(SafariBiome.HAUNTED, true, true, partyGimmiegoldComplete = true))
        assertTrue(floor.enabled(SafariBiome.HAUNTED, true, true)) // party still needs coins
        floor.reset()
        floor.inventory(listOf("Soothing Incense" to 2, "Soothing Incense" to 2))
        floor.inventory(emptyList())
        assertFalse(floor.enabled(SafariBiome.HAUNTED, false, true, partyGimmiegoldComplete = true))
        floor.reset()
        assertTrue(floor.enabled(SafariBiome.HAUNTED, false, true, partyGimmiegoldComplete = true))
    }
}
