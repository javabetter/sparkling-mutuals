package net.johnceo.sparklingmutuals

import net.johnceo.sparklingmutuals.contest.Hive
import net.johnceo.sparklingmutuals.contest.HiveState
import net.johnceo.sparklingmutuals.contest.HoneyHiveRules
import net.johnceo.sparklingmutuals.contest.ContestTracker
import net.johnceo.sparklingmutuals.api.HoneyRefills
import com.google.gson.JsonParser
import java.time.LocalTime
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HoneyHiveRulesTest {
    private val hour = HoneyHiveRules.REFILL_MILLIS

    @Test fun `overlapping loot times cannot permanently swap hive ids`() {
        val pending = linkedMapOf("1,2,3" to 0L, "4,5,6" to 5_000L)
        val refills = linkedMapOf("hive_b" to hour + 5_000, "hive_a" to hour)
        assertTrue(HoneyHiveRules.bind(pending, refills, emptySet()).isEmpty())
        assertTrue(HoneyHiveRules.bind(pending, refills.entries.reversed().associate { it.toPair() }, emptySet()).isEmpty())
    }
    @Test fun `a single refill matching two loots remains unbound`() {
        assertTrue(HoneyHiveRules.bind(mapOf("a" to 0L, "b" to 5_000L), mapOf("hive_a" to hour), emptySet()).isEmpty())
    }
    @Test fun `yesterdays completion is not current at the same time of day`() {
        val now = java.time.LocalDateTime.now()
        val names = listOf("complete", "windowKey", "completedWindow")
        val fields = names.map { ContestTracker.javaClass.getDeclaredField(it).apply { isAccessible = true } }
        val previous = fields.map { it.get(ContestTracker) }
        try {
            fields[0].set(ContestTracker, true)
            fields[1].set(ContestTracker, ContestTracker.windowAt(now.toLocalTime()))
            fields[2].set(ContestTracker, ContestTracker.absoluteWindow(now.minusDays(1)))
            assertFalse(ContestTracker.isComplete())
            assertNull(ContestTracker.completedWindow())
        } finally { fields.zip(previous).forEach { (field, value) -> field.set(ContestTracker, value) } }
    }
    @Test fun `changing profile clears current contest completion and sidebar evidence`() {
        val names = listOf("scope", "complete", "completedWindow", "windowKey", "points", "tier", "tickCounter")
        val fields = names.associateWith { ContestTracker.javaClass.getDeclaredField(it).apply { isAccessible = true } }
        val previous = fields.mapValues { it.value.get(ContestTracker) }
        try {
            ContestTracker.selectScope("player.profile-a")
            fields.getValue("complete").set(ContestTracker, true)
            fields.getValue("completedWindow").set(ContestTracker, ContestTracker.absoluteWindow(java.time.LocalDateTime.now()))
            fields.getValue("tier").set(ContestTracker, "UNCOMMON")
            assertTrue(ContestTracker.isComplete())
            ContestTracker.selectScope("player.profile-b")
            assertFalse(ContestTracker.isComplete())
            assertNull(ContestTracker.completedWindow())
            assertEquals("", ContestTracker.currentTier())
            assertEquals(-1, ContestTracker.currentPoints())
        } finally { fields.forEach { (name, field) -> field.set(ContestTracker, previous.getValue(name)) } }
    }

    @Test fun `your own loot beats a block that still looks full`() {
        assertEquals(HiveState.EMPTY, HoneyHiveRules.state(1000, lootedAt = 0, blockFull = true, refillAt = null, matched = true))
        assertEquals(HiveState.FULL, HoneyHiveRules.state(hour, lootedAt = 0, blockFull = true, refillAt = null, matched = true))
    }
    @Test fun `then the API's pending refill, then the block, then whether the hive is matched`() {
        assertEquals(HiveState.EMPTY, HoneyHiveRules.state(0, null, blockFull = false, refillAt = null, matched = false))
        assertEquals(HiveState.EMPTY, HoneyHiveRules.state(0, null, null, refillAt = 10, matched = true))
        assertEquals(HiveState.FULL, HoneyHiveRules.state(20, null, null, refillAt = 10, matched = true))
        assertEquals(HiveState.FULL, HoneyHiveRules.state(20, null, blockFull = true, refillAt = 10, matched = true))
        assertEquals(HiveState.FULL, HoneyHiveRules.state(0, null, null, null, matched = true))
        assertEquals(HiveState.UNKNOWN, HoneyHiveRules.state(0, null, null, null, matched = false))
    }
    @Test fun `a loot from another session beats a block that looks full`() {
        // Looted elsewhere, no local record: the API's refill an hour out wins over the full-looking block.
        assertEquals(HiveState.EMPTY, HoneyHiveRules.state(1000, null, blockFull = true, refillAt = hour, matched = true))
        // A Queen Bee refill seen after that loot makes it full again, despite the stale API time.
        assertEquals(HiveState.FULL, HoneyHiveRules.state(1000, null, blockFull = true, refillAt = hour, matched = true, queenRefilledAt = 500))
        // A loot after the Queen Bee refill counts again.
        assertEquals(HiveState.EMPTY, HoneyHiveRules.state(2000, null, blockFull = true, refillAt = hour + 1500, matched = true, queenRefilledAt = 500))
    }
    @Test fun `no honey data from the API leaves unloaded matched hives unknown, not full`() {
        assertEquals(HiveState.UNKNOWN, HoneyHiveRules.state(0, null, null, null, matched = true, refillsKnown = false))
        val cloud = JsonParser.parseString(javaClass.getResource("/cloud/profile.json")!!.readText()).asJsonObject
        val member = { body: com.google.gson.JsonObject -> body.getAsJsonArray("profiles")[0].asJsonObject.getAsJsonObject("members")
            .entrySet().first().value.asJsonObject }
        assertEquals(mapOf("hive_8" to 1760000000000L, "hive_12" to 1760000300000L), HoneyRefills.read(member(cloud)))
        val fresh = JsonParser.parseString(javaClass.getResource("/cloud/profile-fresh.json")!!.readText()).asJsonObject
        assertNull(HoneyRefills.read(member(fresh)), "a missing honey section is unknown")
        assertEquals(emptyMap<String, Long>(), HoneyRefills.read(JsonParser.parseString("""{"foraging":{"honey":{}}}""").asJsonObject))
    }
    @Test fun `loots are kept per player and profile`() {
        assertNull(HoneyHiveRules.scope("0123-4567", null))
        val a = HoneyHiveRules.scope("01234567-89ab-cdef-0123-456789abcdef", "0c5b0ffe-b3b7-4ea5-a1d5-c1d3c14d9688")!!
        val b = HoneyHiveRules.scope("01234567-89ab-cdef-0123-456789abcdef", "11111111-2222-3333-4444-555555555555")!!
        assertNotEquals(a, b)
        assertEquals("0c5b0ffe-b3b7-4ea5-a1d5-c1d3c14d9688", HoneyHiveRules.profileId("Profile ID: 0c5b0ffe-b3b7-4ea5-a1d5-c1d3c14d9688"))
        assertNull(HoneyHiveRules.profileId("Party > Farmer: Profile ID: 0c5b0ffe-b3b7-4ea5-a1d5-c1d3c14d9688"))
        assertEquals(Triple(a, "-729,129,187", 5L), HoneyHiveRules.parseLoot("loot.$a.-729,129,187", "5"))
        assertNull(HoneyHiveRules.parseLoot("loot.-729,129,187", "5"), "an entry without an owner is dropped")
    }
    @Test fun `a completed contest does not hide beacons in later contests`() {
        val window = ContestTracker.windowAt(LocalTime.of(12, 20))
        assertTrue(ContestTracker.completeFor(true, window, ContestTracker.windowAt(LocalTime.of(12, 30))))
        assertFalse(ContestTracker.completeFor(true, window, ContestTracker.windowAt(LocalTime.of(12, 40))))
        assertFalse(ContestTracker.completeFor(false, window, window))
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
