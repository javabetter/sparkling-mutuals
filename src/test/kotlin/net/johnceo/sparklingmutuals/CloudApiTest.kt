package net.johnceo.sparklingmutuals

import net.johnceo.sparklingmutuals.api.ApiFailureKind
import net.johnceo.sparklingmutuals.api.CloudApi
import net.johnceo.sparklingmutuals.api.CloudProfiles
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.johnceo.sparklingmutuals.commands.PartyCommand
import net.johnceo.sparklingmutuals.commands.PartyCommandKind
import net.johnceo.sparklingmutuals.commands.PartyLookupResult
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CloudApiTest {
    private fun fixture(name: String): JsonObject =
        JsonParser.parseString(javaClass.getResource("/cloud/$name.json")!!.readText()).asJsonObject
    private val player = "01234567-89ab-cdef-0123-456789abcdef"
    private val profile = "11111111-2222-3333-4444-555555555555"

    @Test fun `the Worker's response carries every field the mod reads`() {
        val body = fixture("profile")
        assertEquals(profile, CloudProfiles.selectedProfileId(body))
        val member = CloudProfiles.member(body, player, profile)!!
        val safari = member.getAsJsonObject("safari")
        assertEquals(listOf("SOLSNATCHER", "GAZER"), safari.getAsJsonArray("discovered_sparkling_critters").map { it.asString })
        assertEquals(39, safari.getAsJsonObject("tickets").get("basic").asInt)
        assertEquals(3150000.5, member.getAsJsonObject("player_data").getAsJsonObject("experience").get("SKILL_HUNTING").asDouble)
        assertEquals(12212, member.getAsJsonObject("currencies").getAsJsonObject("essence").getAsJsonObject("SAFARI").get("current").asInt)
        assertEquals(2, member.getAsJsonObject("foraging").getAsJsonObject("honey").getAsJsonObject("refill_times").size())
        // Nothing beyond those fields (purse, inventories, other skills) is passed on.
        assertFalse(member.toString().contains("coin_purse"))
        assertFalse(member.toString().contains("SKILL_MINING"))
    }
    @Test fun `fields Hypixel leaves out stay missing rather than becoming empty values`() {
        val member = CloudProfiles.member(fixture("profile-fresh"), "00000000000000000000000000000001", profile)!!
        assertFalse(member.getAsJsonObject("foraging").has("honey"), "no honey subtree, not an empty refill list")
        assertFalse(member.getAsJsonObject("player_data").getAsJsonObject("experience").has("SKILL_HUNTING"))
        assertNull(CloudProfiles.member(fixture("profile"), player, "66666666-7777-8888-9999-000000000000"),
            "only the selected profile is served")
    }
    @Test fun `with the cloud API a blank key still runs lookups`() {
        val tickets = PartyCommand(PartyCommandKind.TICKETS, "JohnCEO")
        val reply = PartyLookupResult.fetch(tickets, "", cloud = true) { "JohnCEO: Basic (1), Economy (0), Premium (0), First-Class (0)" }
        assertTrue(reply.successful)
    }
    @Test fun `cloud API errors stay local and map busy to the rate limit`() {
        assertNull(CloudApi.failure(200))
        assertEquals(ApiFailureKind.RATE_LIMIT, CloudApi.failure(429)?.kind)
        assertEquals(ApiFailureKind.UNAVAILABLE, CloudApi.failure(502)?.kind)
        assertEquals(ApiFailureKind.UNAVAILABLE, CloudApi.failure(404)?.kind)
    }
}
