package net.johnceo.sparklingmutuals.safari

/** Run-owned food accounting; each feeder announcement consumes one food. */
class SafariBirdLedger {
    private val foods = listOf("Bag of Seeds", "Wriggleworm", "Yogi Berry")
    private val picked = mutableMapOf<String, Int>()
    private val forest = mutableMapOf<String, Int>()
    private val removed = mutableMapOf<String, Int>()
    private var feedings = 0
    private var macawPairs = 0
    private val spawned = mutableMapOf<String, Int>()
    private var announced = false
    private val startingFoods = mutableMapOf<String, Int>()
    val sparklingTarget get() = 9 + startingFoods.values.sum()
    val sparklingSpawned get() = used >= sparklingTarget
    /** Sample every tick during Head Start; subtract already reported Forest floor pickups. */
    fun startingInventory(stacks: List<Pair<String, Int>>, elapsed: Long) {
        if (elapsed !in 0L..3000L) return
        val held = stacks.groupBy { SafariRules.strip(it.first).trim() }
            .mapValues { (_, items) -> items.sumOf { it.second.coerceAtLeast(0) } }
        foods.forEach { food ->
            startingFoods[food] = maxOf(startingFoods[food] ?: 0, (held[food] ?: 0) - (forest[food] ?: 0))
        }
    }
    val foodsComplete get() = foods.all { (forest[it] ?: 0) >= 3 }
    val allSpawned get() = foodsComplete && used >= collected
    val collected get() = picked.values.sum()
    val used get() = feedings
    fun alert(biome: SafariBiome?, enabled: Boolean): Boolean {
        if (!enabled || biome != SafariBiome.FOREST || !foodsComplete || announced) return false
        announced = true
        return true
    }
    fun pickup(raw: String, biome: SafariBiome?): Boolean {
        val text = SafariRules.strip(raw)
        if (!text.startsWith("FLOOR DROP!")) return false
        val food = foods.firstOrNull { Regex("\\b${Regex.escape(it)}\\b(?! Shard)").containsMatchIn(text) } ?: return false
        picked.merge(food, 1, Int::plus)
        if (biome == SafariBiome.FOREST) forest.merge(food, 1, Int::plus)
        return true
    }
    /** Exact announcements documented by CritterMod 0.9.0 BirdfeederWatch (MIT). */
    fun spawn(raw: String): Boolean {
        val species = when (SafariRules.strip(raw)) {
            "A Bluebird was attracted to the Birdfeeder!" -> "Bluebird"
            "A Parakeet was attracted to the Birdfeeder!" -> "Parakeet"
            "Two Macaws were attracted to the Birdfeeder!" -> { macawPairs++; "Macaw" }
            else -> return false
        }
        spawned.merge(species, if (species == "Macaw") 2 else 1, Int::plus)
        feedings++
        return true
    }
    fun inventory(stacks: List<Pair<String, Int>>) {
        val held = stacks.groupBy { SafariRules.strip(it.first).trim() }.mapValues { (_, items) -> items.sumOf { it.second.coerceAtLeast(0) } }
        foods.forEach { removed[it] = maxOf(removed[it] ?: 0, (picked[it] ?: 0) - (held[it] ?: 0)) }
    }
    private fun pairs(caught: Int, remaining: Int) = maxOf(macawPairs, (caught + remaining.coerceAtLeast(0) + 1) / 2)
    fun hasMacaws(caught: Int, remaining: Int) = pairs(caught, remaining) > 0
    fun requiredCaptures(macawsCaught: Int, macawsRemaining: Int) =
        (collected + pairs(macawsCaught, macawsRemaining) - macawsRemaining.coerceAtLeast(0)).coerceAtLeast(0)
    fun sufficient(catches: Int, macawsCaught: Int, macawsRemaining: Int) = allSpawned &&
        catches >= requiredCaptures(macawsCaught, macawsRemaining)
    fun capturesComplete(count: (String) -> Int) = allSpawned &&
        listOf("Bluebird", "Parakeet", "Macaw").all { species ->
            count(species) >= if (species == "Macaw") minOf(1, spawned[species] ?: 0) else spawned[species] ?: 0
        }
}
