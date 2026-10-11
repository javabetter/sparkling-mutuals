package net.johnceo.sparklingmutuals

import com.google.gson.JsonParser
import net.johnceo.sparklingmutuals.api.SafariProfile
import net.johnceo.sparklingmutuals.commands.SparklingLookup
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SparklingLookupTest {
    @Test fun `profile reads discoveries tickets essence and hunting xp`() {
        val member = JsonParser.parseString("""{"safari":{"discovered_sparkling_critters":["MANTIS_SHRIMP","GEMZIE"],
            "tickets":{"basic":162,"economy":4},"total_captured_sparkling_critters":30},
            "player_data":{"experience":{"SKILL_HUNTING":175.0}},"currencies":{"essence":{"SAFARI":{"current":12212}}}}""").asJsonObject
        val profile = SafariProfile.from(member)
        assertEquals(listOf("Gemzie", "Mantis Shrimp"), profile.caught)
        assertEquals(35, profile.missing.size)
        assertEquals(mapOf("basic" to 162L, "economy" to 4L, "premium" to 0L, "first_class" to 0L), profile.tickets)
        assertEquals(12212L, profile.essence)
        assertEquals(30L, profile.totalCaptured)
        assertEquals(2.0, profile.huntingLevel!!, 1e-9)
    }
    @Test fun `hunting levels follow the skill table and overflow past sixty`() {
        assertEquals(0.5, SafariProfile.levelFromXp(25.0), 1e-9)
        assertEquals(60.0, SafariProfile.levelFromXp(111_672_425.0), 1e-9)
        assertEquals(61.0, SafariProfile.levelFromXp(111_672_425.0 + 7_600_000), 1e-9)
    }
    @Test fun `columns group by biome from most common to rarest`() {
        val columns = SparklingLookup.columns(listOf("Macaw", "Bluebird", "Foxtrot", "Parakeet", "Wumpa", "Gemzie"))
        assertEquals(listOf("Foxtrot", "Bluebird", "Parakeet", "Macaw"), columns[0])
        assertEquals(listOf("Gemzie"), columns[1])
        assertEquals(listOf("Wumpa"), columns[2])
        assertTrue(columns[3].isEmpty())
    }
    @Test fun `shared lists only what everyone has`() {
        assertEquals(listOf("Bluebird", "Gemzie"),
            SparklingLookup.shared(listOf(listOf("Gemzie", "Bluebird", "Macaw"), listOf("Bluebird", "Gemzie"))))
    }
}
