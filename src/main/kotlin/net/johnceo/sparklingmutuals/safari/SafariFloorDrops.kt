package net.johnceo.sparklingmutuals.safari

import net.johnceo.sparklingmutuals.config.ConfigManager
import net.johnceo.sparklingmutuals.config.SafariEspConfig

/** Per-run pickups survive item consumption; a manual choice lasts only for this biome visit. */
class FloorDropState {
    private var gemsReady = false
    private val gems = mutableSetOf<String>()
    private val gemNames = setOf("Purple Gem", "Lime Gem", "Orange Gem")
    private var pickaxes = 0
    private var inventoryPickaxes = 0
    private var incensePickups = 0
    private var incenseReady = false
    private var biome: SafariBiome? = null
    private var override: Boolean? = null
    fun visit(biome: SafariBiome?) {
        if (this.biome != biome) { override = null; this.biome = biome }
    }
    fun record(text: String, biome: SafariBiome?) {
        if (!text.startsWith("FLOOR DROP!")) return
        gems.addAll(gemNames.filter(text::contains))
        if (text.contains("Soothing Incense", true)) incensePickups++
        if (biome == SafariBiome.ICY && Regex("\\bIcebreaker\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) pickaxes++
    }
    fun inventory(stacks: List<Pair<String, Int>>) {
        if (stacks.filter { SafariRules.strip(it.first).equals("Soothing Incense", true) }.sumOf { it.second.coerceAtLeast(0) } >= 4) incenseReady = true
        val heldGems = stacks.filter { it.second > 0 }.map { SafariRules.strip(it.first) }.toSet().intersect(gemNames)
        gems.addAll(heldGems)
        if (heldGems.containsAll(gemNames)) gemsReady = true
        inventoryPickaxes = maxOf(inventoryPickaxes, stacks.filter { SafariRules.strip(it.first).equals("Icebreaker", true) }.sumOf { it.second.coerceAtLeast(0) })
    }
    fun force(enabled: Boolean) { if (biome in setOf(SafariBiome.CAVERN, SafariBiome.ICY)) override = enabled }
    fun enabled(biome: SafariBiome?, fullClear: Boolean, configured: Boolean, forestFoodComplete: Boolean = false,
        partyBirdsComplete: Boolean = false, partyGemzieComplete: Boolean = false,
        partyGimmiegoldComplete: Boolean = false, gemzieCaught: Boolean = false, doomCaught: Boolean = false): Boolean {
        if (biome == this.biome && override != null) return override!!
        return when (biome) {
            SafariBiome.CAVERN -> if (fullClear) !gemsReady else gems.size < 3 && !partyGemzieComplete && !gemzieCaught
            SafariBiome.FOREST -> configured && (fullClear || !partyBirdsComplete && !forestFoodComplete)
            SafariBiome.HAUNTED -> configured && (fullClear || !partyGimmiegoldComplete || !incenseReady && incensePickups < 4 && !doomCaught)
            SafariBiome.ICY -> fullClear && maxOf(pickaxes, inventoryPickaxes) < 1
            else -> configured
        }
    }
    fun enabledForMode(biome: SafariBiome?, mode: SafariMode, configured: Boolean, run: SafariRun?, party: PartySparklingState): Boolean {
        fun caught(name: String) = (run?.count(name) ?: 0) > 0
        fun discoveryComplete(vararg names: String) = party.everyoneHas(*names) &&
            (mode != SafariMode.UNIQUE || names.all(::caught))
        return enabled(biome, mode == SafariMode.FULL_CLEAR, configured, run?.birdFoodsComplete == true,
            discoveryComplete("Bluebird", "Parakeet", "Macaw"), discoveryComplete("Gemzie"), discoveryComplete("Gimmiegold"),
            caught("Gemzie"), caught("Doomspiral"))
    }
    fun reset() { gemsReady = false; gems.clear(); pickaxes = 0; inventoryPickaxes = 0; incensePickups = 0; incenseReady = false; biome = null; override = null }
}

object SafariFloorDrops {
    val state = FloorDropState()
    fun enabled(biome: SafariBiome? = SafariAssist.biome): Boolean {
        state.visit(SafariAssist.biome)
        val run = SafariTracking.ledger.current
        val party = SafariSparklingMode.state
        return state.enabledForMode(biome, SafariFullClear.mode, SafariEspConfig.groups.getValue("floor").enabled, run, party)
    }
}
