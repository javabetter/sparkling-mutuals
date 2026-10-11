package net.johnceo.sparklingmutuals

import net.johnceo.sparklingmutuals.safari.*
import net.johnceo.sparklingmutuals.config.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.Properties
import java.util.UUID
import java.nio.file.Path
import org.junit.jupiter.api.io.TempDir

class CommunalCompletionTest {
    @TempDir lateinit var dir: Path
    private fun party(vararg names: String) = PartySparklingState().apply {
        select(setOf("me")); accept(setOf("me"), mapOf("me" to (SafariRoster.all.map { it.name }.toSet() - names.toSet())))
    }
    @Test fun `party done remains after an empty visit but remaining entities correct Full Clear`() {
        val run = SafariRun(0)
        run.partyFullClear("Party > Friend: fd")
        run.visitBiome(SafariBiome.FOREST)
        run.updateCaptureEvidence(SafariBiome.FOREST, emptySet(), false)
        assertEquals(9, run.fullClearProgress(SafariBiome.FOREST.critters))
        assertEquals(0, run.completed(SafariMode.UNIQUE, SafariBiome.FOREST.critters, party()))
        assertNull(run.completion.announcement(run, SafariBiome.FOREST, SafariMode.FULL_CLEAR, party()))
        run.updateCaptureEvidence(SafariBiome.FOREST, setOf("Foxtrot"), false)
        assertTrue(run.fullClearProgress(SafariBiome.FOREST.critters) < 9)
        assertEquals(0, run.personalCount("Foxtrot"))
    }
    @Test fun `Cavern party checks are corrected only by observed remaining structures`() {
        val run = SafariRun(0)
        run.sparklingChecks.chat("Party > Friend: cd", true)
        run.reconcileStructures(SafariBiome.CAVERN, false, false)
        assertTrue(run.sparklingChecks.checked("Rockmite"))
        run.reconcileStructures(SafariBiome.CAVERN, true, false)
        assertFalse(run.sparklingChecks.checked("Rockmite"))
        assertTrue(run.sparklingChecks.checked("Snoozle"))
        run.reconcileStructures(SafariBiome.CAVERN, false, true)
        assertFalse(run.sparklingChecks.checked("Snoozle"))
        assertTrue(run.sparklingChecks.checked("Gemzie"))
    }
    @Test fun `only prior loot share establishes inherited biomes and cannot fabricate a PB`() {
        val run = SafariRun(0)
        run.record(SafariCatch(SafariRoster.named("Areita")!!, false))
        run.visitBiome(SafariBiome.HAUNTED)
        run.updateCaptureEvidence(SafariBiome.HAUNTED, emptySet(), false)
        assertTrue(run.inherited(SafariBiome.HAUNTED))
        assertTrue(run.captureComplete("Gimmiegold"))
        assertFalse(run.captureComplete("Gimmiegold", true))
        assertTrue(run.sparklingComplete("Gimmiegold", party("Gimmiegold")))
        assertEquals(0, run.count("Gimmiegold"))
        val fresh = SafariRun(0)
        fresh.visitBiome(SafariBiome.HAUNTED)
        fresh.record(SafariCatch(SafariRoster.named("Areita")!!, false))
        assertFalse(fresh.inherited(SafariBiome.HAUNTED))
        val own = SafariRun(0)
        own.record(SafariCatch(SafariRoster.named("Honeybug")!!))
        own.visitBiome(SafariBiome.FOREST)
        assertFalse(own.partyNestsHandled)
    }
    @Test fun `local Sparkling completion announces once without echoing party done`() {
        val run = SafariRun(0); val state = party("Foxtrot")
        run.visitBiome(SafariBiome.FOREST)
        run.updateCaptureEvidence(SafariBiome.FOREST, emptySet(), false)
        run.sparklingChecks.chat("Party > Friend: fd", true)
        assertNull(run.completion.announcement(run, SafariBiome.FOREST, SafariMode.SPARKLING, state))
        run.sparklingChecks.scan(SafariBiome.FOREST, 3.0, 47.0, List(6) { "Foxtrot" to UUID.randomUUID() })
        assertEquals("fd", run.completion.announcement(run, SafariBiome.FOREST, SafariMode.SPARKLING, state))
        assertNull(run.completion.announcement(run, SafariBiome.FOREST, SafariMode.SPARKLING, state))
        assertNull(run.completion.announcement(run, SafariBiome.FOREST, SafariMode.UNIQUE, state))
    }
    @Test fun `run alert is once per run and refuses empty or unknown discovery sets`() {
        val run = SafariRun(0)
        assertFalse(run.completion.alert(run, SafariMode.SPARKLING, PartySparklingState(), true))
        assertFalse(run.completion.alert(run, SafariMode.SPARKLING, party(), true))
        SafariRoster.all.forEach { run.record(SafariCatch(it, false)) }
        assertFalse(run.completion.alert(run, SafariMode.UNIQUE, party(), true))
        run.partyFullClear("Party > Friend: fd"); run.partyFullClear("Party > Friend: cd")
        assertFalse(run.completion.alert(run, SafariMode.UNIQUE, party(), false))
        assertTrue(run.completion.alert(run, SafariMode.UNIQUE, party(), true))
        assertFalse(run.completion.alert(run, SafariMode.UNIQUE, party(), true))
    }
    @Test fun `completion background defaults and customized appearance survive reload`() {
        AppearanceConfig.load(Properties())
        val style = AppearanceConfig.panels.getValue("run_complete")
        assertEquals(80, style.transparency)
        assertEquals(0x33AAFFAA, style.backgroundArgb)
        style.transparency = 45
        AppearanceConfig.runCompleteColor = AppearanceConfig.color(0x123456)
        val saved = Properties(); AppearanceConfig.save(saved); AppearanceConfig.load(saved)
        assertEquals(45, style.transparency)
        assertEquals(0x123456, SafariEspConfig.rgb(AppearanceConfig.runCompleteColor) and 0xFFFFFF)
        AppearanceConfig.load(Properties())
    }
    @Test fun `inherited Cavern keeps mound statistic neutral while remaining structures block completion`() {
        ConfigManager.init(dir); ConfigManager.fullClearMode = true; ConfigManager.showMoundStats = true
        val run = SafariRun(0)
        run.record(SafariCatch(SafariRoster.named("Driftling")!!, false))
        run.visitBiome(SafariBiome.CAVERN)
        run.updateCaptureEvidence(SafariBiome.CAVERN, setOf("Rockmite Mound"), false)
        run.reconcileStructures(SafariBiome.CAVERN, true, true)
        assertFalse(run.captureComplete("Rockmite")); assertFalse(run.captureComplete("Snoozle"))
        assertEquals(net.johnceo.sparklingmutuals.hud.HudRow.WHITE,
            SafariPanels.captures(run, SafariBiome.CAVERN).rows.single { it.label == "Rockmite Mounds" }.valueColor)
        assertTrue(SafariPanels.missing(run, SafariBiome.CAVERN, false, 0, 2).rows.any { it.label == "Mounds to break" })
    }
    @Test fun `run completion alert options persist with bell defaults`() {
        ConfigManager.init(dir)
        assertTrue(ConfigManager.runCompleteAlert)
        assertEquals("Run Complete", ConfigManager.runCompleteTitle)
        assertEquals("minecraft:block.bell.use", ConfigManager.runCompleteSound)
        ConfigManager.runCompleteAlert = false; ConfigManager.runCompleteTitle = "Finished"
        ConfigManager.runCompleteSound = "minecraft:block.note_block.pling"; ConfigManager.runCompleteVolume = 35
        ConfigManager.save(); ConfigManager.init(dir)
        assertFalse(ConfigManager.runCompleteAlert); assertEquals("Finished", ConfigManager.runCompleteTitle)
        assertEquals("minecraft:block.note_block.pling", ConfigManager.runCompleteSound)
        assertEquals(35, ConfigManager.runCompleteVolume)
    }
}
