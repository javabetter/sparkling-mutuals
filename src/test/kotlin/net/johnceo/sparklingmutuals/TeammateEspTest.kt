package net.johnceo.sparklingmutuals

import net.johnceo.sparklingmutuals.safari.TeammateEspRules
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class TeammateEspTest {
    private val local = UUID.randomUUID()
    private val member = UUID.randomUUID()
    @Test fun `only current party UUIDs qualify in SkyBlock`() {
        val roster = listOf(local.toString(), member.toString().uppercase())
        assertTrue(TeammateEspRules.eligible(true, true, member, local, roster))
        assertFalse(TeammateEspRules.eligible(false, true, member, local, roster))
        assertFalse(TeammateEspRules.eligible(true, false, member, local, roster))
        assertFalse(TeammateEspRules.eligible(true, true, local, local, roster))
        assertFalse(TeammateEspRules.eligible(true, true, UUID.randomUUID(), local, roster))
        assertFalse(TeammateEspRules.eligible(true, true, member, local, emptyList()))
    }
    @Test fun `name scale preserves nearby size and grows at distance like Noamm`() {
        assertEquals(1.0, TeammateEspRules.nameScale(0.0))
        assertEquals(1.0, TeammateEspRules.nameScale(5.0))
        assertEquals(12.0, TeammateEspRules.nameScale(100.0))
    }
}
