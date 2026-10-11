package net.johnceo.sparklingmutuals.commands

import net.johnceo.sparklingmutuals.safari.SafariBiome
import net.johnceo.sparklingmutuals.safari.SafariFullClear
import net.johnceo.sparklingmutuals.safari.SafariMode
import net.johnceo.sparklingmutuals.safari.SafariSparklingMode
import net.johnceo.sparklingmutuals.safari.SafariPanels
import net.johnceo.sparklingmutuals.safari.SafariTracking

/** `!m` and `!m <biome>`: what the current mode still needs this run, from the missing panel's own list. */
object SafariMissingReply {
    /** Full names, the short and colloquial forms typed mid-run, and single letters. */
    fun biome(word: String): SafariBiome? = when (word.lowercase()) {
        "c", "cav", "cave", "caves", "cavern" -> SafariBiome.CAVERN
        "f", "for", "fore", "forest", "wood", "woods" -> SafariBiome.FOREST
        "i", "ice", "icy", "frozen", "cold" -> SafariBiome.ICY
        "h", "haunt", "haunted", "spooky", "ghost" -> SafariBiome.HAUNTED
        else -> null
    }
    fun prefix(biome: SafariBiome?) = if (biome == null) "Missing: " else "Missing ${biome.label}: "
    fun format(biome: SafariBiome?, missing: (SafariBiome) -> List<String>): String {
        fun list(b: SafariBiome) = missing(b).ifEmpty { listOf("Done") }.joinToString(", ")
        return prefix(biome) + if (biome != null) list(biome)
            else SafariBiome.entries.joinToString(" | ") { "${it.label}: ${list(it)}" }
    }
    fun current(biome: SafariBiome?): String {
        // Sparkling targets are the party's missing discoveries; until they load, nothing can be called done.
        if (SafariFullClear.mode == SafariMode.SPARKLING && !SafariSparklingMode.state.ready) return prefix(biome) + "discoveries still loading"
        val run = SafariTracking.ledger.current
        return format(biome) { b -> SafariPanels.missingCritters(run, b).map { it.name } }
    }
}
