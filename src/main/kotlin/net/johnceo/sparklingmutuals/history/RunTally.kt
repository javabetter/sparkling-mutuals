package net.johnceo.sparklingmutuals.history

import net.johnceo.sparklingmutuals.safari.SafariCatch

/**
 * What a run's chat says beyond the captures the run itself counts: shards, Rainbow Feathers, Macaws and sparklings.
 * Fed server lines only; party, guild and private chat never reach it.
 */
class RunTally {
    val shards = linkedMapOf<String, Int>()
    val catches = linkedMapOf<String, Int>()
    val sparklings = mutableListOf<String>()
    var feathers = 0
    var macaws = 0
    var caught = 0
    var lootShares = 0
    val party = linkedSetOf<String>()
    var ownSparklings = 0

    fun line(text: String, self: String) {
        if (text == MACAW_BONUS) { feathers++; return }
        sparkling.matchEntire(text)?.let { match ->
            val player = match.groupValues[1]
            sparklings += "$player:${match.groupValues[2].trim()}"
            if (player.equals(self, true)) ownSparklings++
            return
        }
        val catch = SafariCatch.parse(text) ?: return
        val critter = catch.critter.name
        party += critter
        if (catch.personal) { caught++; catches.merge(critter, 1, Int::plus) } else lootShares++
        if (critter == "Macaw") macaws++
        if ("Shard" in text) shards.merge(critter, quantity.find(text)?.groupValues?.get(1)?.toInt() ?: 1, Int::plus)
        feather.find(text)?.let { feathers += it.groupValues[1].toIntOrNull() ?: 1 }
    }

    companion object {
        const val MACAW_BONUS = "MACAW! You found a bonus Rainbow Feather!"
        /** "gained 2x Cavernfish Shard": the quantity, read off the shard itself. */
        val quantity = Regex("""(\d+)x [A-Za-z' ]+? Shard""")
        /** "received a Rainbow Feather" / "received 2x Rainbow Feather" on a sparkling catch or its loot share. */
        val feather = Regex("""(?:(\d+)x |an? )Rainbow Feather""")
        val sparkling = Regex("""^SPARKLING! ([A-Za-z0-9_]{1,16}) caught a SPARKLING (.+?)!$""")
    }
}
