package net.johnceo.sparklingmutuals

import com.google.gson.JsonParser
import net.johnceo.sparklingmutuals.api.SafariProfile
import net.johnceo.sparklingmutuals.history.AchievementTier
import net.johnceo.sparklingmutuals.history.Achievements
import net.johnceo.sparklingmutuals.history.Measure
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AchievementApiTest {
    private fun achievement(id: String) = Achievements.definitions.first { it.id == id }

    @Test fun `runs come from the once-a-run bestiary counts and catches from all 37`() {
        val member = JsonParser.parseString("""{"safari":{"discovered_sparkling_critters":["GAZER"]},
            "bestiary":{"kills":{"doomspiral_1":379,"wumpa_1":372,"hideyho_1":368,"cavernfish_1":1000,"mantis_shrimp_1":20}}}""").asJsonObject
        val profile = SafariProfile.from(member)
        assertEquals(379L, profile.runs)
        assertEquals(379L + 372 + 368 + 1000 + 20, profile.catches)
        assertEquals(listOf("Gazer"), profile.caught)
    }
    @Test fun `a profile without bestiary data leaves runs and catches unknown`() {
        val profile = SafariProfile.from(JsonParser.parseString("""{"safari":{"discovered_sparkling_critters":[]}}""").asJsonObject)
        assertNull(profile.runs)
        assertNull(profile.catches)
    }
    @Test fun `sparkle runs are re-tiered and a lone hat trick tops them`() {
        assertEquals(AchievementTier.LEGENDARY, achievement("party_double").tier)
        assertEquals(AchievementTier.DIVINE, achievement("party_triple").tier)
        assertEquals(AchievementTier.SPECIAL, achievement("own_triple").tier)
        assertTrue(AchievementTier.SPECIAL > AchievementTier.DIVINE)
    }
    @Test fun `250 runs without a unique is Divine, the Macaw drought stops at 8 and emblems are gone`() {
        val noUnique = achievement("no_unique_runs_250")
        assertEquals(AchievementTier.DIVINE, noUnique.tier)
        assertEquals(Measure.DRY_UNIQUE, noUnique.measure)
        assertEquals(250L, noUnique.goal)
        assertEquals(listOf(3L, 5L, 8L), Achievements.definitions.filter { it.measure == Measure.RUNS_SINCE_MACAW }.map { it.goal })
        assertTrue(Achievements.definitions.none { it.id.startsWith("emblem") })
        assertEquals(Achievements.definitions, Achievements.all())
    }
}
