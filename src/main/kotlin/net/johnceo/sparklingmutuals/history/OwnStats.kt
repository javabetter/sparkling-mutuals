package net.johnceo.sparklingmutuals.history

import net.johnceo.sparklingmutuals.LocalChat
import net.johnceo.sparklingmutuals.api.HypixelApi
import net.johnceo.sparklingmutuals.hud.SkyblockSidebar
import net.minecraft.client.Minecraft
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors

/**
 * Your own Safari numbers from Hypixel's API (your key, or the shared cloud API), so achievements and the dex count
 * everything from before the mod was installed: the sparkling dex, runs (the highest of the Doomspiral, Wumpa and
 * Hideyho bestiary counts, one of each per run) and critters caught (the 37 Safari critters' bestiary counts). Read
 * when you join SkyBlock, then every ten minutes and after each run.
 */
object OwnStats {
    private const val REFRESH_MILLIS = 10 * 60_000L
    private val logger = LoggerFactory.getLogger("sparkling-mutuals/own-stats")
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "Sparkling Mutuals own stats").apply { isDaemon = true } }
    @Volatile var dex: Set<String> = emptySet()
        private set
    @Volatile var runs = 0L
        private set
    @Volatile var caught = 0L
        private set
    private var fetchedAt = 0L
    private var fetching = false

    /** Read again soon, e.g. after a run ends. */
    fun refreshSoon() { fetchedAt = 0 }

    fun tick(client: Minecraft) {
        val now = System.currentTimeMillis()
        if (fetching || now - fetchedAt < REFRESH_MILLIS || !HypixelApi.provider.enabled || !SkyblockSidebar.inSkyblock(client)) return
        val uuid = client.player?.uuid?.toString() ?: return
        fetching = true; fetchedAt = now
        executor.submit {
            val result = runCatching { HypixelApi.getSafariProfile(uuid) }
            client.execute {
                fetching = false
                result.onFailure { logger.info("Own Safari stats lookup failed: {}", it.message) }.onSuccess { profile ->
                    dex = profile.caught.toSet()
                    profile.runs?.let { runs = it }
                    profile.catches?.let { caught = it }
                    val earned = Achievements.afterApiStats()
                    if (earned > 0) client.player?.sendSystemMessage(LocalChat.component(
                        "[SM] Your Hypixel profile earns §e$earned§r achievement${if (earned == 1) "" else "s"}. See /sm achievements."))
                }
            }
        }
    }

    fun reset() { fetchedAt = 0; dex = emptySet(); runs = 0; caught = 0 }
}
