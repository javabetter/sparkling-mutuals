package net.johnceo.sparklingmutuals.contest

enum class HiveState { FULL, EMPTY }

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
     * Every hive is tracked on its own by one number, when it refills: empty until then, full after. Nothing else
     * brings a hive back early, the block's look included, since hives are looted per player and the block goes on
     * looking full to you after you've emptied it. A hive never looted is full.
     */
    fun state(now: Long, refillAt: Long?): HiveState = if (refillAt != null && now < refillAt) HiveState.EMPTY else HiveState.FULL

    /**
     * Folds the API's refill time for a hive into its own: the later one wins, since the API can lag a loot just seen
     * here. A time from a loot before the hive's last Queen Bee proc is stale (the API's time runs a second or so past
     * the loot, hence the tolerance), and one already passed changes nothing.
     */
    fun merge(current: Long?, apiRefill: Long, queenAt: Long?, now: Long): Long? = when {
        apiRefill <= now -> current
        queenAt != null && apiRefill - REFILL_MILLIS <= queenAt + MATCH_TOLERANCE -> current
        else -> maxOf(current ?: apiRefill, apiRefill)
    }

    /** Hive loots belong to one player's SkyBlock profile; unknown until both are known. */
    fun scope(player: String?, profile: String?): String? =
        if (player.isNullOrBlank() || profile.isNullOrBlank()) null else "${player.replace("-", "")}.${profile.replace("-", "")}"
    private val profileId = Regex("^Profile ID: ([0-9a-fA-F-]{32,36})$")
    /** Hypixel sends `Profile ID: <uuid>` whenever you join a SkyBlock server. */
    fun profileId(line: String): String? = profileId.matchEntire(line.trim())?.groupValues?.get(1)?.lowercase()

    /** `<prefix>.<scope>.<x,y,z>` entries back to (scope, key, time); entries without an owner are dropped. */
    fun parseLoot(name: String, value: String, prefix: String = "loot"): Triple<String, String, Long>? {
        val rest = name.removePrefix("$prefix.").takeIf { it != name } ?: return null
        val cut = rest.lastIndexOf('.').takeIf { it > 0 } ?: return null
        val time = value.toLongOrNull() ?: return null
        return Triple(rest.substring(0, cut), rest.substring(cut + 1), time)
    }

    fun nearest(hives: Collection<Hive>, x: Double, y: Double, z: Double): Hive? = hives
        .map { it to (it.x + .5 - x).let { d -> d * d } + (it.y + .5 - y).let { d -> d * d } + (it.z + .5 - z).let { d -> d * d } }
        .filter { it.second <= LOOT_RADIUS * LOOT_RADIUS }.minByOrNull { it.second }?.first

    /** Bind only unambiguous pairs; close loots must never persist the wrong position-to-id mapping. */
    fun bind(pending: Map<String, Long>, refills: Map<String, Long>, taken: Set<String>): Map<String, String> {
        val candidates = pending.mapValues { (_, lootedAt) ->
            refills.filter { (id, at) -> id !in taken && kotlin.math.abs(at - (lootedAt + REFILL_MILLIS)) <= MATCH_TOLERANCE }.keys
        }
        return candidates.filterValues { it.size == 1 }
            .filter { (_, ids) -> candidates.values.count { ids.single() in it } == 1 }
            .mapValues { (_, ids) -> ids.single() }
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
