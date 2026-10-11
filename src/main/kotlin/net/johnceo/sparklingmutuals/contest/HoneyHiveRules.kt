package net.johnceo.sparklingmutuals.contest

enum class HiveState { FULL, EMPTY, UNKNOWN }

/** A honeyhive position and, once matched, the id Hypixel's API knows it by (`hive_8`). */
data class Hive(val x: Int, val y: Int, val z: Int, var id: String? = null) {
    val key get() = "$x,$y,$z"
}

/** The decisions behind the honeyhive beacons, free of the client so they can be tested. */
object HoneyHiveRules {
    /** Measured against the API: a looted hive refills an hour later, to the second. */
    const val REFILL_MILLIS = 3_600_000L
    /** How close a refill time has to be to loot + an hour to count as that loot. */
    const val MATCH_TOLERANCE = 15_000L
    /** How far from a hive the loot message can be and still be taken as that hive's. */
    const val LOOT_RADIUS = 8.0

    /**
     * Best evidence first: your own loot (hives are looted per player, so the block can still look full to you),
     * then the block itself when it's loaded, then the API's refill time for a matched hive. A matched hive with no
     * refill pending is full; an unmatched one we know nothing about is unknown.
     */
    fun state(now: Long, lootedAt: Long?, blockFull: Boolean?, refillAt: Long?, matched: Boolean): HiveState = when {
        lootedAt != null && now < lootedAt + REFILL_MILLIS -> HiveState.EMPTY
        blockFull != null -> if (blockFull) HiveState.FULL else HiveState.EMPTY
        refillAt != null -> if (now < refillAt) HiveState.EMPTY else HiveState.FULL
        matched -> HiveState.FULL
        else -> HiveState.UNKNOWN
    }

    fun nearest(hives: Collection<Hive>, x: Double, y: Double, z: Double): Hive? = hives
        .map { it to (it.x + .5 - x).let { d -> d * d } + (it.y + .5 - y).let { d -> d * d } + (it.z + .5 - z).let { d -> d * d } }
        .filter { it.second <= LOOT_RADIUS * LOOT_RADIUS }.minByOrNull { it.second }?.first

    /** Loots seen here (key -> time) matched to API refill times one hour later; returns key -> hive id. */
    fun bind(pending: Map<String, Long>, refills: Map<String, Long>, taken: Set<String>): Map<String, String> {
        val bound = linkedMapOf<String, String>()
        for ((key, lootedAt) in pending) {
            val id = refills.entries.firstOrNull { (id, at) ->
                id !in taken && id !in bound.values && kotlin.math.abs(at - (lootedAt + REFILL_MILLIS)) <= MATCH_TOLERANCE
            }?.key ?: continue
            bound[key] = id
        }
        return bound
    }

    /** Lines of `x y z id` (id `-` while unmatched); `#` starts a comment. */
    fun parse(text: String): List<Hive> = text.lines().mapNotNull { line ->
        val parts = line.substringBefore('#').trim().split(Regex("\\s+"))
        if (parts.size < 3) return@mapNotNull null
        val (x, y, z) = parts.take(3).map { it.toIntOrNull() ?: return@mapNotNull null }
        Hive(x, y, z, parts.getOrNull(3)?.takeIf { it != "-" })
    }

    fun countdown(left: Long): String {
        val seconds = maxOf(0L, left / 1000)
        return "Refills in " + if (seconds >= 60) "${seconds / 60}m" else "${seconds}s"
    }
}
