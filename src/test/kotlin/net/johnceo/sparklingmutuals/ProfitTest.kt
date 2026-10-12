package net.johnceo.sparklingmutuals

import net.johnceo.sparklingmutuals.history.BazaarPrices
import net.johnceo.sparklingmutuals.history.ProfitDays
import net.johnceo.sparklingmutuals.history.RunRecord
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ProfitTest {
    private val table = mapOf("SHARD_CAVERNFISH" to doubleArrayOf(100.0, 250.0), "SHARD_MANTIS_SHRIMP" to doubleArrayOf(1_000.0, 2_000.0),
        BazaarPrices.FEATHER to doubleArrayOf(4_000_000.0, 4_600_000.0))

    @Test fun `prices are refetched once the table is half an hour old`() {
        var fetches = 0
        val original = BazaarPrices.startFetch
        BazaarPrices.startFetch = { fetches++ }
        try {
            assertTrue(BazaarPrices.accept(table, now = 0))
            BazaarPrices.value(mapOf("Cavernfish" to 1), 0, now = 10 * 60_000L)
            assertEquals(0, fetches, "a fresh table is used as it is")
            assertEquals(250L, BazaarPrices.value(mapOf("Cavernfish" to 1), 0, instaSell = false, now = 31 * 60_000L))
            assertEquals(1, fetches, "an old table still answers, and a refresh starts")
            BazaarPrices.value(mapOf("Cavernfish" to 1), 0, now = 32 * 60_000L)
            assertEquals(1, fetches, "only one refresh at a time")
            assertFalse(BazaarPrices.accept(emptyMap(), now = 33 * 60_000L), "a failed fetch keeps the old prices")
            assertTrue(BazaarPrices.ready())
            BazaarPrices.value(mapOf("Cavernfish" to 1), 0, now = 33 * 60_000L + 30_000)
            assertEquals(1, fetches)
            BazaarPrices.value(mapOf("Cavernfish" to 1), 0, now = 34 * 60_000L + 1)
            assertEquals(2, fetches, "and retries a minute later")
        } finally { BazaarPrices.startFetch = original; BazaarPrices.accept(emptyMap(), now = 0) }
    }
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
