package net.johnceo.sparklingmutuals

import net.johnceo.sparklingmutuals.history.BazaarPrices
import net.johnceo.sparklingmutuals.history.BazaarPriceCache
import net.johnceo.sparklingmutuals.history.ProfitDays
import net.johnceo.sparklingmutuals.history.RunRecord
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ProfitTest {
    @Test fun `valuing runs refreshes loaded prices after thirty minutes`() {
        var now = 2_000_000L
        var quote = 250.0
        var pending: ((Map<String, DoubleArray>) -> Unit)? = null
        val cache = BazaarPriceCache({ now }) { complete -> pending = complete }
        cache.ensureFresh()
        pending!!(mapOf("SHARD_CAVERNFISH" to doubleArrayOf(100.0, quote)))
        pending = null
        assertEquals(250L, cache.value(mapOf("Cavernfish" to 1), 0, instaSell = false))
        assertNull(pending, "fresh prices are reused without another request")
        now += 1_800_000L
        assertEquals(250L, cache.value(mapOf("Cavernfish" to 1), 0, instaSell = false))
        assertNotNull(pending, "using a stale populated cache starts a refresh")
        val inFlight = pending
        cache.value(mapOf("Cavernfish" to 1), 0, instaSell = false)
        assertSame(inFlight, pending, "a run ending during a refresh must not start a second request")
        quote = 500.0
        pending!!(mapOf("SHARD_CAVERNFISH" to doubleArrayOf(100.0, quote)))
        pending = null
        assertEquals(500L, cache.value(mapOf("Cavernfish" to 1), 0, instaSell = false))
        assertNull(pending, "fresh prices are reused without another request")
    }
    @Test fun `failed refresh retains loaded prices and retries after a minute`() {
        var now = 2_000_000L
        var pending: ((Map<String, DoubleArray>) -> Unit)? = null
        val cache = BazaarPriceCache({ now }) { complete -> pending = complete }
        cache.ensureFresh()
        pending!!(mapOf("SHARD_CAVERNFISH" to doubleArrayOf(100.0, 250.0)))
        pending = null
        now += 1_800_000L
        cache.value(mapOf("Cavernfish" to 1), 0, instaSell = false)
        assertNotNull(pending)
        pending!!(emptyMap())
        pending = null
        assertEquals(250L, cache.value(mapOf("Cavernfish" to 1), 0, instaSell = false))
        assertNull(pending)
        now += 60_000L
        cache.value(mapOf("Cavernfish" to 1), 0, instaSell = false)
        assertNotNull(pending)
    }
    @Test fun `explicit price tables can be valued without fetching live prices`() {
        val cache = BazaarPriceCache({ 2_000_000L }) { fail<Unit>("explicit tables must not request live prices") }
        assertEquals(250L, cache.value(mapOf("Cavernfish" to 1), 0, table, instaSell = false))
        assertEquals(-1L, cache.value(mapOf("Cavernfish" to 1), 0, emptyMap(), instaSell = false))
    }
    private val table = mapOf("SHARD_CAVERNFISH" to doubleArrayOf(100.0, 250.0), "SHARD_MANTIS_SHRIMP" to doubleArrayOf(1_000.0, 2_000.0),
        BazaarPrices.FEATHER to doubleArrayOf(4_000_000.0, 4_600_000.0))

    @Test fun `shards and feathers are valued at sell offer unless insta-sell is chosen`() {
        val shards = mapOf("Cavernfish" to 4, "Mantis Shrimp" to 1, "Unlisted" to 9)
        assertEquals(4 * 250L + 2_000 + 4_600_000, BazaarPrices.value(shards, 1, table, instaSell = false))
        assertEquals(4 * 100L + 1_000 + 4_000_000, BazaarPrices.value(shards, 1, table, instaSell = true))
        assertEquals(-1L, BazaarPrices.value(shards, 1, hashMapOf()))
    }
    @Test fun `coins format compactly and unknown shows a dash`() {
        assertEquals("-", BazaarPrices.format(-1)); assertEquals("950", BazaarPrices.format(950))
        assertEquals("19k", BazaarPrices.format(19_400)); assertEquals("2.85M", BazaarPrices.format(2_850_000))
        assertEquals("5.52B", BazaarPrices.format(5_520_000_000))
    }
    @Test fun `profit groups runs by day with totals, rate and best run`() {
        fun at(day: Int, hour: Int) = LocalDate.of(2026, 10, day).atTime(hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val runs = listOf(RunRecord(number = 1, start = at(9, 10), length = 1_800_000, coins = 1_000_000),
            RunRecord(number = 2, start = at(9, 11), length = 1_800_000, coins = 3_000_000),
            RunRecord(number = 3, start = at(10, 9), length = 600_000, coins = -1))
        val days = ProfitDays.of(runs, ZoneOffset.UTC)
        assertEquals(listOf(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 9)), days.map { it.date })
        assertEquals(4_000_000L, days[1].coins); assertEquals(4_000_000L, days[1].perHour); assertEquals(2, days[1].best?.number)
        assertEquals(0L, days[0].coins, "unknown coins count as nothing, not minus one")
    }
}
