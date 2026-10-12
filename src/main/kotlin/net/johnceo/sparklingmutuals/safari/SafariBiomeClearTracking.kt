package net.johnceo.sparklingmutuals.safari

import net.johnceo.sparklingmutuals.LocalChat

import net.johnceo.sparklingmutuals.config.ConfigManager
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

/** Clear evidence is collected even with every HUD and ESP switch disabled. */
object SafariBiomeClearTracking {
    private var ticks = 0
    fun tick(client: Minecraft) {
        if (++ticks < 10) return
        ticks = 0
        val run = SafariTracking.ledger.current ?: return
        if (client.level == null) return
        val player = client.player ?: return
        val biome = SafariAssist.biome ?: return
        if (SafariAssist.location != SafariLocation.INSIDE) return
        val snapshot = SafariEsp.observations()
        if (!snapshot.scanned) return
        snapshot.critters.filterNot { it.mound }.forEach { run.observeCritter(it.id, it.species, it.uuid) }
        run.updateCaptureEvidence(biome, snapshot.critters.filter {
            it.biome == biome
        }.map { if (it.mound) "Rockmite Mound" else it.species }.toSet(),
            SafariStructures.walls.states.size == 5 && SafariStructures.walls.allBroken, BeeNests.allChecked,
            macawsInRange = snapshot.critters.count { it.biome == biome && it.species == "Macaw" },
            playerX = player.x, playerZ = player.z, nestsPunched = BeeNests.punchedCount)
        run.sparklingChecks.scan(biome, player.x, player.z,
            snapshot.critters.filterNot { it.mound }.map { it.species to it.uuid },
            nestsChecked = BeeNests.allChecked,
            moundsCleared = run.allMoundsBroken && SafariStructures.nearbyMounds == 0 && snapshot.critters.none { it.mound },
            wallsCleared = SafariStructures.walls.states.size == 5 && SafariStructures.walls.allBroken,
            birdsSpawned = run.birds.sparklingSpawned,
            hauntedDropsRemaining = snapshot.floorDrops.count { SafariEspRules.biomeAt(it.x.toDouble(), it.z.toDouble()) == SafariBiome.HAUNTED })
        run.reconcileStructures(biome, SafariStructures.nearbyMounds > 0 || snapshot.critters.any { it.mound },
            SafariStructures.walls.states.any { it == WallState.INTACT })
        SafariCompletion.tick(client, run, biome)
        // A scan can prove the last Unique critter absent after its biome's final catch.
        SafariTracking.checkPartyUnique(client, biome)
        if (biome in run.biomeClears) return
        // A completed entity scan is sufficient; unloaded surrounding chunks must not block a PB.
        val evidence = BiomeClearEvidence(snapshot.scanned,
            snapshot.critters.count { it.biome == biome && !it.mound },
            run.moundsComplete() &&
                SafariStructures.nearbyMounds == 0 && snapshot.critters.none { it.mound },
            SafariStructures.walls.states.size == 5 && SafariStructures.walls.allBroken, BeeNests.allChecked,
            nearbyMacaws = snapshot.critters.count { it.biome == biome && !it.mound && it.species == "Macaw" })
        SafariFullClear.recordClear(run, biome, evidence, ConfigManager.personalBests, System.currentTimeMillis())?.let {
            ConfigManager.save(); player.sendSystemMessage(LocalChat.component(it))
        }
    }
}
