package net.johnceo.sparklingmutuals

import net.johnceo.sparklingmutuals.contest.Hive
import net.johnceo.sparklingmutuals.contest.HiveState
import net.johnceo.sparklingmutuals.contest.HoneyHiveRules
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HoneyHiveRulesTest {
    private val hour = HoneyHiveRules.REFILL_MILLIS

    @Test fun `your own loot beats a block that still looks full`() {
        assertEquals(HiveState.EMPTY, HoneyHiveRules.state(1000, lootedAt = 0, blockFull = true, refillAt = null, matched = true))
        assertEquals(HiveState.FULL, HoneyHiveRules.state(hour, lootedAt = 0, blockFull = true, refillAt = null, matched = true))
    }
    @Test fun `then the block, then the API, then whether the hive is matched`() {
        assertEquals(HiveState.EMPTY, HoneyHiveRules.state(0, null, blockFull = false, refillAt = null, matched = false))
        assertEquals(HiveState.EMPTY, HoneyHiveRules.state(0, null, null, refillAt = 10, matched = true))
        assertEquals(HiveState.FULL, HoneyHiveRules.state(20, null, null, refillAt = 10, matched = true))
        assertEquals(HiveState.FULL, HoneyHiveRules.state(0, null, null, null, matched = true))
        assertEquals(HiveState.UNKNOWN, HoneyHiveRules.state(0, null, null, null, matched = false))
    }
    @Test fun `loots bind to the refill an hour later, one hive each`() {
        val bound = HoneyHiveRules.bind(linkedMapOf("1,2,3" to 0L, "4,5,6" to 60_000L),
            mapOf("hive_1" to hour + 5_000, "hive_2" to 60_000 + hour - 3_000, "hive_3" to hour + 40_000), taken = setOf("hive_9"))
        assertEquals(mapOf("1,2,3" to "hive_1", "4,5,6" to "hive_2"), bound)
        assertTrue(HoneyHiveRules.bind(mapOf("1,2,3" to 0L), mapOf("hive_1" to hour), taken = setOf("hive_1")).isEmpty())
    }
    @Test fun `the nearest hive within loot range`() {
        val hives = listOf(Hive(0, 64, 0), Hive(5, 64, 0), Hive(40, 64, 0))
        assertEquals(hives[1], HoneyHiveRules.nearest(hives, 5.0, 65.0, 1.0))
        assertNull(HoneyHiveRules.nearest(hives, 20.0, 65.0, 0.0))
    }
    @Test fun `the bundled survey parses with ids and unmatched hives`() {
        val text = javaClass.getResourceAsStream("/assets/sparkling-mutuals/honeyhives.txt")!!.bufferedReader().readText()
        val hives = HoneyHiveRules.parse(text)
        assertEquals(19, hives.size)
        assertTrue(hives.any { it.id == null })
        assertEquals("hive_5", hives.first { it.key == "-729,129,187" }.id)
        assertEquals("Refills in 42m", HoneyHiveRules.countdown(42 * 60_000L + 500))
        assertEquals("Refills in 30s", HoneyHiveRules.countdown(30_999))
    }
}
