package net.johnceo.sparklingmutuals.contest

import net.johnceo.sparklingmutuals.LocalChat

import net.johnceo.sparklingmutuals.config.ContestConfig
import net.johnceo.sparklingmutuals.hud.SkyblockSidebar
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import java.time.LocalTime
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

object ContestTracker {

    private const val FIRST_START_MINUTE = 15
    private const val WINDOW_SECONDS = 1200
    private const val END_GAP_SECONDS = 30
    private const val STALE_GUARD_SECONDS = 10
    private const val MAX_SOUND_COPIES = 20

    private val TIER_ROW = Regex("""^\s*([A-Za-z]+)\s+with\s+(\S+)""")

    private val completeTiers = setOf(
        "UNCOMMON",
        "RARE",
        "EPIC",
        "LEGENDARY",
        "MYTHIC",
        "DIVINE",
        "SPECIAL"
    )

    private var tickCounter = 0
    private var windowKey = -1L
    private var secondsIntoWindow = 0
    private var complete = false
    private var points = -1
    private var tier = ""

    private val warnedMinutes = linkedSetOf<Int>()

    fun onClientTick(client: Minecraft) {
        if (!ContestConfig.trackContest) return
        if (client.player == null || client.level == null) return

        if (++tickCounter < 10) return
        tickCounter = 0

        rollWindow()
        readSidebar(client)
        maybeWarn(client)
    }

    /** The contest window a time of day falls in. */
    fun windowAt(time: LocalTime): Long =
        (Math.floorMod(time.toSecondOfDay() - FIRST_START_MINUTE * 60, 86400) / WINDOW_SECONDS).toLong()

    private fun rollWindow() {
        val now = LocalTime.now()
        val shifted = Math.floorMod(
            now.toSecondOfDay() - FIRST_START_MINUTE * 60,
            86400
        )
        val key = windowAt(now)

        secondsIntoWindow = shifted % WINDOW_SECONDS

        if (key == windowKey) return

        val first = windowKey == -1L
        windowKey = key
        complete = false
        warnedMinutes.clear()
        points = -1
        tier = ""

        if (!first) {
            println("Sparkling Mutuals: Contest window rolled at $now")
        }
    }

    fun secondsToEnd(): Int =
        max(0, WINDOW_SECONDS - END_GAP_SECONDS - secondsIntoWindow)

    fun secondsToNextStart(): Int =
        WINDOW_SECONDS - secondsIntoWindow

    fun betweenContests(): Boolean =
        secondsToEnd() == 0

    private fun readSidebar(client: Minecraft) {
        for (line in MiriaContestFilter.miriaLines(SkyblockSidebar.lines(client))) {
            val match = TIER_ROW.find(line) ?: continue
            val foundTier = match.groupValues[1]

            if (!isTierName(foundTier)) continue

            tier = foundTier
            points = parseInt(match.groupValues[2])
            break
        }

        if (
            tier.isNotEmpty() &&
            !complete &&
            secondsIntoWindow >= STALE_GUARD_SECONDS &&
            tier.uppercase() in completeTiers
        ) {
            complete = true
            println("Sparkling Mutuals: Contest complete — $tier with $points")
        }
    }

    private fun maybeWarn(client: Minecraft) {
        if (
            complete ||
            !ContestConfig.contestWarnEnabled ||
            betweenContests() ||
            !SkyblockSidebar.inSkyblock(client)
        ) {
            return
        }

        val remaining = secondsToEnd()
        val due = ContestConfig.warningMinutes().filter { remaining <= it * 60 && it !in warnedMinutes }
        if (due.isEmpty()) return
        warnedMinutes.addAll(due)
        // Joining late produces one warning instead of all elapsed warnings at once.
        client.player?.sendSystemMessage(
                    LocalChat.component(
                        "§b[Sparkling Mutuals] §c§lContest not complete§r — " +
                                "${formatRemaining(remaining)} left."
                    )
        )

        playSound(client)

        if (ContestConfig.contestWarnTitle) {
            showTitle(client, remaining)
        }
    }

    private fun playSound(client: Minecraft) {
        val id = Identifier.tryParse(ContestConfig.contestSound.trim()) ?: return
        val sound = BuiltInRegistries.SOUND_EVENT
            .getOptional(id)
            .orElse(null)
            ?: run {
                println(
                    "Sparkling Mutuals: Unknown contest sound ${ContestConfig.contestSound}"
                )
                return
            }

        val wanted = max(0f, ContestConfig.contestSoundVolume / 100f)
        val copies = min(MAX_SOUND_COPIES, ceil(wanted.toDouble()).toInt())

        for (i in 0 until copies) {
            val gain = min(1f, wanted - i)

            client.soundManager.play(
                SimpleSoundInstance.forUI(sound, 1.0f, gain)
            )
        }
    }

    private fun showTitle(client: Minecraft, remaining: Int) {
        client.gui.setTimes(5, 60, 15)
        client.gui.setTitle(Component.literal("§c§lINCOMPLETE!"))
        client.gui.setSubtitle(
            Component.literal(
                "§e${formatRemaining(remaining)} left to reach Uncommon"
            )
        )
    }

    /**
     * Completion only counts for the window it was seen in. With contest tracking off the window stops rolling, so a
     * completion from an earlier contest must not carry over into later ones.
     */
    fun isComplete(): Boolean = completeFor(complete, windowKey, windowAt(LocalTime.now()))
    fun completeFor(complete: Boolean, seenWindow: Long, currentWindow: Long) = complete && seenWindow == currentWindow

    fun currentTier(): String = tier

    fun currentPoints(): Int = points

    fun isActive(): Boolean =
        windowKey != -1L && !betweenContests()

    fun secondsIntoWindow(): Int = secondsIntoWindow

    fun formatRemaining(seconds: Int): String =
        String.format("%d:%02d", seconds / 60, seconds % 60)

    private fun isTierName(word: String): Boolean {
        val upper = word.uppercase()
        return upper == "COMMON" || upper in completeTiers
    }

    private fun parseInt(token: String): Int {
        var text = token.trim().lowercase().replace(",", "")

        val scale = when {
            text.endsWith("k") -> {
                text = text.dropLast(1)
                1000.0
            }
            text.endsWith("m") -> {
                text = text.dropLast(1)
                1_000_000.0
            }
            else -> 1.0
        }

        return try {
            round(text.toDouble() * scale).toInt()
        } catch (_: NumberFormatException) {
            -1
        }
    }
}
