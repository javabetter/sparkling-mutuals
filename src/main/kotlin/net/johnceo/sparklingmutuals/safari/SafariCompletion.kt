package net.johnceo.sparklingmutuals.safari

import net.johnceo.sparklingmutuals.config.ConfigManager
import net.johnceo.sparklingmutuals.hud.SafariHud
import net.johnceo.sparklingmutuals.hud.SmallAlerts
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier

/** Run-owned notification memory never changes capture or PB evidence. */
class SafariCompletionEvents {
    private val announced = mutableSetOf<Pair<SafariMode, SafariBiome>>()
    private var alerted = false
    fun announcement(run: SafariRun, biome: SafariBiome, mode: SafariMode, party: PartySparklingState): String? {
        if (!run.hasCaptureEvidence(biome) || mode == SafariMode.SPARKLING && !party.ready) return null
        val targets = biome.critters.filter { mode != SafariMode.SPARKLING || party.needs(it.name) }
        if (targets.isEmpty() || run.completed(mode, targets, party, localOnly = true) != targets.size ||
            !announced.add(mode to biome)) return null
        return when (biome) { SafariBiome.FOREST -> "fd"; SafariBiome.CAVERN -> "cd"; SafariBiome.ICY -> "id"; SafariBiome.HAUNTED -> "hd" }
    }
    fun alert(run: SafariRun, mode: SafariMode, party: PartySparklingState, enabled: Boolean): Boolean {
        if (!enabled || alerted || mode == SafariMode.SPARKLING && !party.ready) return false
        val targets = SafariRoster.all.filter { mode != SafariMode.SPARKLING || party.needs(it.name) }
        if (targets.isEmpty() || run.completed(mode, targets, party) != targets.size) return false
        alerted = true
        return true
    }
}

object SafariCompletion {
    private var owner: SafariRun? = null
    private val queue = ArrayDeque<Pair<SafariBiome, String>>()
    private var sentAt = 0L
    fun tick(client: Minecraft, run: SafariRun, biome: SafariBiome) {
        if (owner !== run) { owner = run; queue.clear(); sentAt = 0 }
        val mode = SafariFullClear.mode
        val party = SafariSparklingMode.state
        run.completion.announcement(run, biome, mode, party)?.let { queue.addLast(biome to it) }
        // Revalidate queued completions after contradictory local evidence or a mode change.
        queue.removeAll { (area, _) -> run.completed(mode,
            area.critters.filter { mode != SafariMode.SPARKLING || party.needs(it.name) }, party, localOnly = true) !=
            area.critters.count { mode != SafariMode.SPARKLING || party.needs(it.name) } }
        val now = System.currentTimeMillis()
        if (client.connection != null && queue.isNotEmpty() && now - sentAt >= 3000) {
            client.connection!!.sendCommand("pc ${queue.removeFirst().second}"); sentAt = now
        }
        if (run.completion.alert(run, mode, party, ConfigManager.runCompleteAlert)) {
            SmallAlerts.show(SafariHud.RUN_COMPLETE)
            playSound(client)
        }
    }
    fun playSound(client: Minecraft) {
        if (ConfigManager.runCompleteVolume <= 0) return
        val id = Identifier.tryParse(ConfigManager.runCompleteSound) ?: return
        val sound = BuiltInRegistries.SOUND_EVENT.getOptional(id).orElse(null) ?: return
        client.soundManager.play(SimpleSoundInstance.forUI(sound, 1f, ConfigManager.runCompleteVolume.coerceIn(0, 100) / 100f))
    }
}
