package net.johnceo.sparklingmutuals.history

/** One saved Safari run. Mutable with defaults throughout, so Gson can fill it and older files still load. */
class RunRecord(
    var number: Int = 0,
    /** When the run began (arriving in the Safari). */
    var start: Long = 0,
    /** Arrival to leaving, in milliseconds. */
    var length: Long = 0,
    /** The mode it was played in: UNIQUE, FULL_CLEAR or SPARKLING. */
    var mode: String = "",
    /** Different critters the party secured (your captures and loot shares). */
    var party: Int = 0,
    /** Different critters you caught yourself. */
    var you: Int = 0,
    /** Your captures, repeats included. */
    var caught: Int = 0,
    /** Teammates' catches you got a share of. */
    var lootShares: Int = 0,
    var partySize: Int = 1,
    /** Safari tickets used for this run. */
    var tickets: Int = 0,
    /** Critter -> shards received (captures and loot shares). */
    var shards: MutableMap<String, Int> = linkedMapOf(),
    /** Critter -> your captures of it. */
    var catches: MutableMap<String, Int> = linkedMapOf(),
    /** Rainbow Feathers: one per sparkling caught or shared, plus Macaw bonuses. */
    var feathers: Int = 0,
    /** Sparklings caught in the run, as "player:critter". */
    var sparklings: MutableList<String> = mutableListOf(),
    var ownSparklings: Int = 0,
    /** Macaws the party secured. */
    var macaws: Int = 0,
    /** Biome -> time from the start of the run until the mode had it complete. */
    var splits: MutableMap<String, Long> = linkedMapOf(),
    /** Rebuilt from old logs: no splits, and the party count is what the logs showed. */
    var imported: Boolean = false,
) {
    fun hasSparkling() = sparklings.isNotEmpty()
}

/** One caught sparkling: yours or anyone's announced in your chat. */
class SparklingEntry(
    var at: Long = 0,
    var player: String = "",
    var critter: String = "",
    var unique: Boolean = false,
    /** Tickets the party used since the previous sparkling. */
    var tickets: Int? = null,
)
