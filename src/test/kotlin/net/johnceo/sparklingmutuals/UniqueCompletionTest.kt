package net.johnceo.sparklingmutuals

import net.johnceo.sparklingmutuals.safari.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class UniqueCompletionTest {
    private fun party(vararg needed: String) = PartySparklingState().apply {
        select(setOf("me")); accept(setOf("me"), mapOf("me" to (SafariRoster.all.map { it.name }.toSet() - needed.toSet())))
    }
    private fun caught(run: SafariRun, vararg names: String) = names.forEach { run.record(SafariCatch(SafariRoster.named(it)!!)) }
    private fun complete(run: SafariRun, name: String, party: PartySparklingState) =
        run.completed(SafariMode.UNIQUE, listOf(SafariRoster.named(name)!!), party) == 1

    @Test fun `Cavern special catches wait for local sparkle checks and unvisited party done`() {
        val run = SafariRun(0); val state = party("Rockmite", "Snoozle")
        caught(run, "Rockmite", "Snoozle", "Driftling")
        assertFalse(complete(run, "Rockmite", state)); assertTrue(complete(run, "Driftling", state))
        run.partyFullClear("Party > Friend: cd")
        assertTrue(complete(run, "Rockmite", state)); assertTrue(complete(run, "Snoozle", state))
        run.visitBiome(SafariBiome.CAVERN)
        run.reconcileStructures(SafariBiome.CAVERN, true, true)
        assertFalse(complete(run, "Rockmite", state)); assertFalse(complete(run, "Snoozle", state))
        run.sparklingChecks.scan(SafariBiome.CAVERN, 0.0, 0.0, emptyList(), moundsCleared = true, wallsCleared = true)
        assertTrue(complete(run, "Rockmite", state)); assertTrue(complete(run, "Snoozle", state))
        assertTrue(complete(run, "Rockmite", party()))
    }
    @Test fun `Forest catches still need three punched nests and unique bugs plus all bird food fed`() {
        val run = SafariRun(0); val state = party("Honeybug", "Bluebird")
        run.visitBiome(SafariBiome.FOREST); caught(run, "Honeybug", "Bluebird", "Parakeet", "Macaw")
        run.sparklingChecks.scan(SafariBiome.FOREST, 3.0, 47.0, List(3) { "Honeybug" to UUID.randomUUID() }, nestsChecked = true)
        assertFalse(complete(run, "Honeybug", state))
        run.updateCaptureEvidence(SafariBiome.FOREST, emptySet(), false, true, nestsPunched = 2)
        assertFalse(complete(run, "Honeybug", state))
        run.updateCaptureEvidence(SafariBiome.FOREST, emptySet(), false, true, nestsPunched = 3)
        assertTrue(complete(run, "Honeybug", state))
        run.updateCaptureEvidence(SafariBiome.FOREST, emptySet(), false, false, nestsPunched = 3)
        assertFalse(complete(run, "Honeybug", state))
        assertFalse(complete(run, "Bluebird", state)); assertFalse(complete(run, "Parakeet", state))
        repeat(8) { run.birds.spawn("A Bluebird was attracted to the Birdfeeder!") }
        assertFalse(complete(run, "Bluebird", state))
        run.birds.spawn("Two Macaws were attracted to the Birdfeeder!")
        assertTrue(complete(run, "Bluebird", state)); assertTrue(complete(run, "Parakeet", state))
        repeat(10) { run.birds.pickup("FLOOR DROP! Bag of Seeds", SafariBiome.FOREST) }
        assertFalse(complete(run, "Bluebird", state))
        run.birds.spawn("A Parakeet was attracted to the Birdfeeder!")
        assertTrue(complete(run, "Bluebird", state))
        assertTrue(complete(run, "Honeybug", party()))
    }
    @Test fun `Unique local completion announces once and never echoes remote completion`() {
        val run = SafariRun(0); val state = party()
        SafariBiome.CAVERN.critters.forEach { caught(run, it.name) }
        run.partyFullClear("Party > Friend: cd")
        assertNull(run.completion.announcement(run, SafariBiome.CAVERN, SafariMode.UNIQUE, state))
        run.visitBiome(SafariBiome.CAVERN); run.updateCaptureEvidence(SafariBiome.CAVERN, emptySet(), true)
        assertEquals("cd", run.completion.announcement(run, SafariBiome.CAVERN, SafariMode.UNIQUE, state))
        assertNull(run.completion.announcement(run, SafariBiome.CAVERN, SafariMode.UNIQUE, state))
    }
    @Test fun `Icy and Haunted Unique need only one capture regardless of discovery needs`() {
        val run = SafariRun(0); val state = party(*SafariRoster.all.map { it.name }.toTypedArray())
        (SafariBiome.ICY.critters + SafariBiome.HAUNTED.critters).forEach { caught(run, it.name); assertTrue(complete(run, it.name, state)) }
    }
    @Test fun `Unique missing panel matches special progress and party completion adds no personal captures`() {
        net.johnceo.sparklingmutuals.config.ConfigManager.fullClearMode = false
        net.johnceo.sparklingmutuals.config.ConfigManager.sparklingMode = false
        val run = SafariRun(0); val state = party()
        run.record(SafariCatch(SafariRoster.named("Honeybug")!!, false))
        assertTrue(SafariPanels.missing(run, SafariBiome.FOREST, true, 0, party = state).rows.any { it.label == "Honeybug" })
        run.partyFullClear("Party > Friend: fd")
        assertFalse(SafariPanels.missing(run, SafariBiome.FOREST, true, 0, party = state).rows.any { it.label == "Honeybug" })
        assertEquals(0, run.personalCount("Honeybug"))
        assertFalse(complete(run, "Macaw", state))
    }
}
