package net.johnceo.sparklingmutuals.safari

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.johnceo.sparklingmutuals.LocalChat
import net.johnceo.sparklingmutuals.config.ConfigManager
import net.johnceo.sparklingmutuals.config.SafariTheme
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import org.slf4j.LoggerFactory
import kotlin.math.floor

/** Where Hideyho can hide and the mansion's outline: the pure part of [HideyhoSpotter], so it can be tested. */
object HideyhoHiding {
    data class Spot(val x: Int, val y: Int, val z: Int) {
        override fun toString() = "$x, $y, $z"
    }
    /** How close Hideyho has to be to a listed spot to count as settled there, in blocks. */
    const val SPOT_RADIUS = 3.5

    /** Every spot Hideyho has been seen hiding at in the Safari. */
    val SPOTS = listOf(
        Spot(13, 71, -88), Spot(-4, 68, -64), Spot(19, 70, -24), Spot(-28, 72, -80), Spot(-4, 71, -79),
        Spot(37, 70, -14), Spot(-20, 71, -80), Spot(-6, 87, -63), Spot(13, 79, -50), Spot(27, 71, -53),
        Spot(12, 71, -61), Spot(0, 79, -78), Spot(-13, 79, -84), Spot(-13, 79, -57), Spot(0, 71, -56),
        Spot(-7, 79, -83), Spot(-17, 71, -59), Spot(18, 79, -63), Spot(-21, 79, -78), Spot(-8, 87, -70))

    /**
     * The mansion's footprint, corners (x, z) in walking order. The building isn't a box and its floors sit at
     * different heights, so only the outline decides inside or outside.
     */
    val MANSION = listOf(-28.5 to -46.5, -28.5 to -75.5, -23.5 to -75.5, -24.5 to -85.5, 10.0 to -87.0, 10.0 to -84.0,
        17.0 to -84.0, 17.0 to -76.0, 21.0 to -76.0, 21.0 to -55.0, 17.0 to -55.0, 17.0 to -47.0)

    /** The listed spot an entity at this position is settled on, if any (block positions, as the server sends them). */
    fun nearestSpot(x: Double, y: Double, z: Double, spots: List<Spot> = SPOTS): Spot? {
        val bx = floor(x).toInt(); val by = floor(y).toInt(); val bz = floor(z).toInt()
        return spots.map { it to ((it.x - bx) * (it.x - bx) + (it.y - by) * (it.y - by) + (it.z - bz) * (it.z - bz)).toDouble() }
            .filter { it.second <= SPOT_RADIUS * SPOT_RADIUS }.minByOrNull { it.second }?.first
    }

    /** Ray-casting point-in-polygon on the mansion's footprint. */
    fun insideMansion(x: Double, z: Double, corners: List<Pair<Double, Double>> = MANSION): Boolean {
        if (corners.size < 3) return false
        var inside = false
        var j = corners.size - 1
        for (i in corners.indices) {
            val (xi, zi) = corners[i]
            val (xj, zj) = corners[j]
            if ((zi > z) != (zj > z) && x < (xj - xi) * (z - zi) / (zj - zi) + xi) inside = !inside
            j = i
        }
        return inside
    }
    fun inside(spot: Spot) = insideMansion(spot.x + .5, spot.z + .5)

    /** Hideyho running off to hide, and being found (the quest is over). NPC lines only, never relayed chat. */
    private val accepted = Regex("^\\[MOB] Hideyho: (?:Close your eyes and then come find me!|No peeking!)$")
    private val found = Regex("^\\[MOB] Hideyho: (?:Aah! You found me!|Hehe, you found me!)$")
    fun accepted(text: String) = accepted.matches(text)
    fun found(text: String) = found.matches(text)
}

/**
 * Says whether Hideyho hid inside or outside the mansion, as a title and a chat line in the mod's theme. Adapted
 * from Shiny Hunter's HideyhoSpotter (MIT).
 *
 * Accepting the quest arms a watch. Hideyho vanishes and is re-sent at its hiding spot, surfacing from below, so the
 * verdict waits until it is within a few blocks of a known spot. With no quest running, a Hideyho already sitting on
 * a spot is called out once, which covers walking into the Safari with it already hidden.
 */
object HideyhoSpotter {
    private val logger = LoggerFactory.getLogger("sparkling-mutuals/hideyho")
    private const val WATCH_MILLIS = 90_000L
    private const val SCAN_TICKS = 5
    private const val IDLE_TICKS = 20

    private var armedAt = 0L
    private var spotBefore: HideyhoHiding.Spot? = null
    private var announced: HideyhoHiding.Spot? = null
    private var ticks = 0
    private var level: Any? = null

    fun register() {
        ClientReceiveMessageEvents.GAME.register { message, overlay ->
            if (!overlay) Minecraft.getInstance().execute { chat(SafariRules.strip(message.string).trim()) }
        }
    }

    private fun chat(text: String) {
        if (!ConfigManager.hideyhoInsideOutside) return
        when {
            HideyhoHiding.accepted(text) -> {
                if (armedAt == 0L) spotBefore = position()?.let { (x, y, z) -> HideyhoHiding.nearestSpot(x, y, z) }
                armedAt = System.currentTimeMillis()
            }
            HideyhoHiding.found(text) -> armedAt = 0L
        }
    }

    fun onClientTick(client: Minecraft) {
        if (client.level !== level) { reset(); level = client.level }
        if (!ConfigManager.hideyhoInsideOutside || client.player == null || !SafariAssist.inSafari) { armedAt = 0L; return }
        if (++ticks < if (armedAt == 0L) IDLE_TICKS else SCAN_TICKS) return
        ticks = 0
        if (armedAt != 0L && System.currentTimeMillis() - armedAt > WATCH_MILLIS) { armedAt = 0L; return }
        val (x, y, z) = position() ?: return // between vanishing and being re-sent
        val spot = HideyhoHiding.nearestSpot(x, y, z) ?: return // surfacing, or somewhere not on the list
        if (armedAt != 0L) {
            // Still on the spot it was at when the quest was accepted: wait for it to move.
            if (spot == spotBefore) return
            armedAt = 0L
        } else if (spot == announced) return
        announce(client, spot)
    }

    private fun position(): Triple<Double, Double, Double>? =
        SafariEsp.observations().critters.firstOrNull { it.species == "Hideyho" }?.let { Triple(it.x, it.y, it.z) }

    private fun announce(client: Minecraft, spot: HideyhoHiding.Spot) {
        announced = spot
        val inside = HideyhoHiding.inside(spot)
        logger.info("Hideyho settled at {}: {}", spot, if (inside) "inside" else "outside")
        client.gui.setTimes(5, 50, 15)
        client.gui.setTitle(LocalChat.hideyho(inside, bold = true))
        client.gui.setSubtitle(Component.literal(spot.toString()).withStyle { it.withColor(SafariTheme.MUTED) })
        client.player?.sendSystemMessage(LocalChat.component(LocalChat.hideyho(inside)))
    }

    fun reset() { armedAt = 0L; spotBefore = null; announced = null; ticks = 0 }
}
