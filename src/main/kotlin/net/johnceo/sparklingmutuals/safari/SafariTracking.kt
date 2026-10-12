package net.johnceo.sparklingmutuals.safari

import net.johnceo.sparklingmutuals.LocalChat

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.johnceo.sparklingmutuals.config.ConfigManager
import net.johnceo.sparklingmutuals.contest.ContestGui
import net.johnceo.sparklingmutuals.hud.SafariHud
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

/** Tracking is independent of display toggles. Last-run data lives until the next arrival. */
object SafariTracking {
    val ledger = SafariLedger()
    private var level: Any? = null
    val run get() = ledger.displayed
    val displayBiome get() = SafariAssist.biome ?: run?.lastBiome
    val visible get() = SafariVisibility.visible(ConfigManager.showWhere, SafariAssist.inSafari, SafariAssist.atEntrance)
    fun register() {
        ClientReceiveMessageEvents.GAME.register { message, overlay ->
            if (!overlay) Minecraft.getInstance().execute {
                SafariMessages.forRun(message.string, ledger.current, SafariAssist.inSafari).forEach(::receive)
            }
        }
    }
    private fun syncWorld(client: Minecraft) {
        if (client.level !== level) { ledger.worldChanged(System.currentTimeMillis()); resetEncounters(); level = client.level }
    }
    private fun resetEncounters() { BeeNests.reset(); SparklingEncounters.reset(); SafariStructures.reset(); SafariEsp.clearCaptured(); SafariInventoryAlerts.reset(); SafariFloorDrops.state.reset() }
    private fun receive(raw: String) {
        val client = Minecraft.getInstance()
        if (client.level == null || client.player == null) return
        syncWorld(client)
        val text = SafariRules.strip(raw)
        val now = System.currentTimeMillis()
        if (SafariAssist.inSafari) {
            SafariEspRules.thrownSpecies(text)?.let(SafariEsp::threw)
            SafariEspRules.escapedSpecies(text)?.let(SafariEsp::escaped)
            SafariEsp.shywormMessage(text, now)
        }
        if (SafariMessages.enteredBy(text, client.player!!.name.string)) {
            val previous = ledger.current
            ledger.confirmEntry(now); SafariAssist.markEntered()
            if (previous !== ledger.current) resetEncounters()
        }
        if (SafariMounds.outcome(text) != null) {
            if (SafariAssist.inSafari) { ensureRun(client); ledger.current?.recordMound(text) }
            return
        }
        if (SafariAssist.inSafari) {
            ensureRun(client)
            ledger.current?.sparklingChecks?.chat(text, manualAllowed = SafariFullClear.mode == SafariMode.SPARKLING)
            if (ledger.current?.birds?.spawn(text) == true) return
        }
        if (text.startsWith("FLOOR DROP!") && SafariAssist.inSafari) {
            ensureRun(client); ledger.current?.recordBirdFood(text, SafariAssist.biome)
            SafariFloorDrops.state.record(text, SafariAssist.biome)
            return
        }
        val catch = SafariCatch.parse(text) ?: return
        SafariAssist.markEntered()
        ensureRun(client)
        ledger.current!!.record(catch)
        checkPartyUnique(client, catch.critter.biome, now)
        if (catch.personal) {
            SafariEsp.caught(catch.critter.name)
            ConfigManager.personalBests.recordUnique(catch.critter.biome, ledger.current!!, now)?.let { notice ->
                ConfigManager.save(); client.player?.sendSystemMessage(LocalChat.component(notice))
            }
        }
        ConfigManager.personalBests.captureFeedback(text, ledger.current!!, now, client.player!!.name.string)?.let { feedback ->
            if (feedback.changed) ConfigManager.save()
            client.player?.sendSystemMessage(LocalChat.component(feedback.notice))
        }
    }
    fun onClientTick(client: Minecraft) = ensureRun(client)
    fun ensureRun(client: Minecraft) {
        syncWorld(client)
        val previous = ledger.current
        val now = System.currentTimeMillis()
        ledger.update(SafariAssist.location, SafariAssist.biome, now)
        if (previous !== ledger.current) resetEncounters()
    }
    fun finish() { ledger.leave(System.currentTimeMillis()); resetEncounters() }
    fun render(graphics: GuiGraphicsExtractor) {
        val client = Minecraft.getInstance()
        if (client.player == null || client.screen is ContestGui || !visible) return
        val run = run
        val unique = ConfigManager.countUniqueOnly
        if (ConfigManager.progressHud) SafariPanels.progress(run, unique, System.currentTimeMillis()).draw(graphics, SafariHud.PROGRESS)
        if (!SafariVisibility.biomePanels(ConfigManager.showWhere, SafariAssist.inSafari, ledger.current != null, SafariAssist.biome)) return
        val biome = SafariAssist.biome ?: return
        if (ConfigManager.missingPanel) SafariPanels.missing(run, biome, unique, BeeNests.unpunchedCount,
            SafariStructures.nearbyMounds, SafariStructures.walls).draw(graphics, SafariHud.MISSING)
        if (ConfigManager.catchCountPanel) SafariPanels.captures(run, biome).draw(graphics, SafariHud.CAPTURES)
    }
    /** Party Unique PBs are checked after catches and after scans (absence evidence), see recordPartyUnique. */
    fun checkPartyUnique(client: Minecraft, biome: SafariBiome, now: Long = System.currentTimeMillis()) {
        if (SafariFullClear.mode != SafariMode.UNIQUE) return
        val run = ledger.current ?: return
        ConfigManager.personalBests.recordPartyUnique(biome, run, now)?.let { notice ->
            ConfigManager.save(); client.player?.sendSystemMessage(LocalChat.component(notice))
        }
    }
}
