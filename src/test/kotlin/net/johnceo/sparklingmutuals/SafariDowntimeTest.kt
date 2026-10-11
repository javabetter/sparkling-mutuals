package net.johnceo.sparklingmutuals

import net.johnceo.sparklingmutuals.safari.SafariDowntime
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SafariDowntimeTest {
    @BeforeEach fun clear() = SafariDowntime.reset()

    @Test fun `a request in the Safari waits for the run to end and only the requester announces it`() {
        assertNull(SafariDowntime.chat("Party > [MVP+] Farmer: !dt food", inSafari = true, self = "Other"))
        assertFalse(SafariDowntime.holding)
        assertNull(SafariDowntime.leftSafari("Other"))
        assertTrue(SafariDowntime.holding)
        SafariDowntime.reset()
        SafariDowntime.chat("Party > Farmer: !dt food", inSafari = true, self = "Farmer")
        assertEquals("Farmer has requested downtime for reason: food", SafariDowntime.leftSafari("Farmer"))
    }
    @Test fun `asked outside the Safari it holds straight away`() {
        assertEquals("Farmer has requested downtime", SafariDowntime.chat("Party > Farmer: !DT", inSafari = false, self = "Farmer"))
        assertTrue(SafariDowntime.holding)
    }
    @Test fun `the hold lasts until every requester says r`() {
        SafariDowntime.chat("Party > Farmer: !dt", inSafari = true, self = "Me")
        SafariDowntime.chat("Party > Steve: !dt brb", inSafari = true, self = "Me")
        SafariDowntime.leftSafari("Me")
        SafariDowntime.chat("Party > Alex: r", inSafari = false, self = "Me")
        assertTrue(SafariDowntime.holding, "r from someone who didn't ask changes nothing")
        SafariDowntime.chat("Party > farmer: r", inSafari = false, self = "Me")
        assertTrue(SafariDowntime.holding)
        assertEquals("Steve", SafariDowntime.holders())
        SafariDowntime.chat("Party > Steve: R", inSafari = false, self = "Me")
        assertFalse(SafariDowntime.holding)
    }
    @Test fun `only party chat counts`() {
        assertNull(SafariDowntime.chat("Guild > Farmer: !dt", inSafari = false, self = "Farmer"))
        assertNull(SafariDowntime.chat("Party > Farmer: !dtx", inSafari = false, self = "Farmer"))
        assertFalse(SafariDowntime.holding)
    }
}
