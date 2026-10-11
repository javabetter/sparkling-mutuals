package net.johnceo.sparklingmutuals

import net.johnceo.sparklingmutuals.api.ApiFailureKind
import net.johnceo.sparklingmutuals.api.CloudApi
import net.johnceo.sparklingmutuals.commands.PartyCommand
import net.johnceo.sparklingmutuals.commands.PartyCommandKind
import net.johnceo.sparklingmutuals.commands.PartyLookupResult
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CloudApiTest {
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
