package net.johnceo.sparklingmutuals.history

import com.google.gson.JsonParser
import net.johnceo.sparklingmutuals.config.ConfigManager
import net.minecraft.client.Minecraft
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Locale

/**
 * Bazaar prices for critter shards and Rainbow Feathers, for run profit. Read from Hypixel's public bazaar endpoint,
 * which needs no key: an ordinary HTTP request, nothing sent to the game server. Every critter's shard is listed as
 * `SHARD_<NAME>` (`SHARD_MANTIS_SHRIMP`). Refreshed at most every half hour, and only when something wants a price.
 */
object BazaarPrices {
    private const val URL = "https://api.hypixel.net/v2/skyblock/bazaar"
    const val FEATHER = "RAINBOW_FEATHER"
    private const val REFRESH_MILLIS = 30 * 60_000L
    private val logger = LoggerFactory.getLogger("sparkling-mutuals/bazaar")
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    /** Product id -> [instant sell, sell offer]. */
    @Volatile private var prices: Map<String, DoubleArray> = emptyMap()
    private var fetchedAt = 0L
    private var fetching = false

    fun ensureFresh() {
        if (fetching || System.currentTimeMillis() - fetchedAt < REFRESH_MILLIS) return
        fetching = true
        val request = HttpRequest.newBuilder(URI.create(URL)).timeout(Duration.ofSeconds(20)).header("User-Agent", "Sparkling Mutuals").GET().build()
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete { response, error ->
            val read = runCatching {
                check(error == null && response.statusCode() == 200) { error?.message ?: "HTTP ${response.statusCode()}" }
                JsonParser.parseString(response.body()).asJsonObject.getAsJsonObject("products").entrySet()
                    .filter { it.key.startsWith("SHARD_") || it.key == FEATHER }
                    .associate { (id, product) -> product.asJsonObject.getAsJsonObject("quick_status").let {
                        id to doubleArrayOf(it.get("sellPrice").asDouble, it.get("buyPrice").asDouble) } }
            }.onFailure { logger.warn("Bazaar lookup failed: {}", it.message) }.getOrDefault(emptyMap())
            Minecraft.getInstance().execute {
                fetching = false
                if (read.isEmpty()) { fetchedAt = System.currentTimeMillis() - REFRESH_MILLIS + 60_000; return@execute } // retry in a minute
                prices = read; fetchedAt = System.currentTimeMillis()
                RunHistory.fillMissingCoins()
            }
        }
    }

    fun ready() = prices.isNotEmpty()
    fun shardId(critter: String) = "SHARD_" + critter.uppercase(Locale.ROOT).replace(' ', '_')

    /** A run's shards and feathers in coins, or -1 while prices aren't loaded. Sell offer unless insta-sell is chosen. */
    fun value(shards: Map<String, Int>, feathers: Int, table: Map<String, DoubleArray> = prices,
        instaSell: Boolean = ConfigManager.runCoinsInstaSell): Long {
        if (table.isEmpty()) { if (table === prices) ensureFresh(); return -1 }
        fun price(id: String) = table[id]?.let { if (instaSell) it[0] else it[1] } ?: 0.0
        return Math.round(shards.entries.sumOf { (critter, n) -> price(shardId(critter)) * n } + price(FEATHER) * feathers)
    }

    /** `2.85M`, `19k`, `5.52B`; a dash while unknown. */
    fun format(coins: Long): String = when {
        coins < 0 -> "-"
        coins >= 1_000_000_000L -> "%.2fB".format(Locale.ROOT, coins / 1e9)
        coins >= 1_000_000L -> "%.2fM".format(Locale.ROOT, coins / 1e6)
        coins >= 1_000L -> "${Math.round(coins / 1e3)}k"
        else -> coins.toString()
    }
}

/** Profit by day, newest first, for the Profit tab. */
object ProfitDays {
    data class Day(val date: java.time.LocalDate, val runs: Int, val coins: Long, val millis: Long, val best: RunRecord?) {
        val perHour get() = if (millis <= 0) 0L else coins * 3_600_000L / millis
    }
    fun of(runs: List<RunRecord>, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): List<Day> =
        runs.groupBy { java.time.Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate() }
            .map { (date, day) -> Day(date, day.size, day.sumOf { maxOf(0L, it.coins) }, day.sumOf { it.length }, day.maxByOrNull { it.coins }) }
            .sortedByDescending { it.date }
}
