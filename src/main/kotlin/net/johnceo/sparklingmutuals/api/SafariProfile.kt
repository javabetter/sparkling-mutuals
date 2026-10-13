package net.johnceo.sparklingmutuals.api

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import net.johnceo.sparklingmutuals.safari.SafariRoster

/** One player's Safari stats from their selected SkyBlock profile, for `/sparkling`. */
data class SafariProfile(val discoveries: Set<String>, val tickets: Map<String, Long>, val huntingXp: Double?,
    val essence: Long?, val totalCaptured: Long?, val runs: Long? = null, val catches: Long? = null) {
    /** Roster names of the discovered sparklings, in roster order. */
    val caught get() = SafariRoster.all.filter { key(it.name) in discoveries }.map { it.name }
    val missing get() = SafariRoster.all.filter { key(it.name) !in discoveries }.map { it.name }
    val huntingLevel get() = huntingXp?.let(::levelFromXp)

    companion object {
        val TICKET_TIERS = listOf("basic", "economy", "premium", "first_class")
        fun key(name: String) = name.uppercase(java.util.Locale.ROOT).replace(' ', '_')
        /** A critter's bestiary id (`mantis_shrimp_1`). */
        fun bestiaryId(name: String) = key(name).lowercase(java.util.Locale.ROOT) + "_1"
        /** One of each per run, so the highest of their bestiary kills is the number of runs. */
        private val ONCE_A_RUN = listOf("Doomspiral", "Wumpa", "Hideyho")

        fun from(member: JsonObject): SafariProfile {
            fun at(path: String): JsonElement? = path.split('.').fold(member as JsonElement?) { node, step ->
                node?.takeIf { it.isJsonObject }?.asJsonObject?.get(step) }
            fun number(path: String) = at(path)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
            val safari = at("safari")?.takeIf { it.isJsonObject }?.asJsonObject ?: throw ApiFailure.dataUnavailable()
            val discoveries = safari.getAsJsonArray("discovered_sparkling_critters")?.map { it.asString }?.toSet()
                ?: throw ApiFailure.dataUnavailable()
            val tickets = TICKET_TIERS.associateWith { number("safari.tickets.$it")?.asLong ?: 0L }
            // Bestiary kills of the Safari critters are your own catches.
            val kills = at("bestiary.kills")?.takeIf { it.isJsonObject }?.asJsonObject
            fun kills(name: String) = kills?.get(bestiaryId(name))?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong ?: 0L
            return SafariProfile(discoveries, tickets, number("player_data.experience.SKILL_HUNTING")?.asDouble,
                number("currencies.essence.SAFARI.current")?.asLong, number("safari.total_captured_sparkling_critters")?.asLong,
                kills?.let { ONCE_A_RUN.maxOf(::kills) }, kills?.let { SafariRoster.all.sumOf { kills(it.name) } })
        }

        /** SkyBlock's shared skill table, levels 1-60. */
        private val perLevel = longArrayOf(
            50, 125, 200, 300, 500, 750, 1000, 1500, 2000, 3500,
            5000, 7500, 10000, 15000, 20000, 30000, 50000, 75000, 100000, 200000,
            300000, 400000, 500000, 600000, 700000, 800000, 900000, 1000000, 1100000, 1200000,
            1300000, 1400000, 1500000, 1600000, 1700000, 1800000, 1900000, 2000000, 2100000, 2200000,
            2300000, 2400000, 2500000, 2600000, 2750000, 2900000, 3100000, 3400000, 3700000, 4000000,
            4300000, 4600000, 4900000, 5200000, 5500000, 5800000, 6100000, 6400000, 6700000, 7000000)

        /** Fractional level; past 60 each level costs the last plus a slope from 600k that doubles every ten (SkyHanni's overflow). */
        fun levelFromXp(xp: Double): Double {
            var level = 0
            var remaining = maxOf(0.0, xp)
            for (need in perLevel) {
                if (remaining < need) return level + remaining / need
                remaining -= need; level++
            }
            var slope = 600_000L
            var need = 7_000_000L + slope
            while (remaining >= need) {
                remaining -= need; level++
                if (level % 10 == 0) slope *= 2
                need += slope
            }
            return level + remaining / need
        }
    }
}
