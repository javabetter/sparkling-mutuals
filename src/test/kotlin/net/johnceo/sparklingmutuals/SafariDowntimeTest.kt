package net.johnceo.sparklingmutuals

import net.johnceo.sparklingmutuals.config.ConfigManager
import net.johnceo.sparklingmutuals.safari.SafariDowntime
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SafariDowntimeTest {
    @BeforeEach fun clear() { ConfigManager.downtimeCommand = true; SafariDowntime.reset() }
    @AfterEach fun restore() { ConfigManager.downtimeCommand = true; SafariDowntime.reset() }

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
    @Test fun `a requester ready before the run ends cancels their pending downtime`() {
        SafariDowntime.chat("Party > Farmer: !dt food", inSafari = true, self = "Farmer")
        SafariDowntime.chat("Party > farmer: R", inSafari = true, self = "Farmer")
        assertNull(SafariDowntime.leftSafari("Farmer"))
        assertFalse(SafariDowntime.holding)
        assertEquals("", SafariDowntime.holders())
    }
    @Test fun `a pending resume only cancels its own request`() {
        SafariDowntime.chat("Party > Farmer: !dt", inSafari = true, self = "Me")
        SafariDowntime.chat("Party > Steve: !dt", inSafari = true, self = "Me")
        SafariDowntime.chat("Party > Alex: r", inSafari = true, self = "Me")
        SafariDowntime.chat("Party > Farmer: r", inSafari = true, self = "Me")
        assertEquals("Steve", SafariDowntime.holders())
        assertFalse(SafariDowntime.holding)
        SafariDowntime.leftSafari("Me")
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
    @Test fun `a requester leaving the party takes their request with them`() {
        SafariDowntime.chat("Party > Farmer: !dt", inSafari = true, self = "Me")
        SafariDowntime.chat("Party > Steve: !dt", inSafari = true, self = "Me")
        SafariDowntime.leftSafari("Me")
        SafariDowntime.chat("[MVP+] Farmer has left the party.", inSafari = false, self = "Me")
        assertTrue(SafariDowntime.holding)
        assertEquals("Steve", SafariDowntime.holders())
        SafariDowntime.chat("[VIP] Steve has been removed from the party.", inSafari = false, self = "Me")
        assertFalse(SafariDowntime.holding, "nobody is left who could say r")
        // Pending requests (run still going) are dropped the same way.
        SafariDowntime.chat("Party > Alex: !dt", inSafari = true, self = "Me")
        SafariDowntime.chat("Alex was removed from your party because they disconnected", inSafari = true, self = "Me")
        assertNull(SafariDowntime.leftSafari("Me"))
        assertFalse(SafariDowntime.holding)
        // Someone who never asked leaving changes nothing.
        SafariDowntime.chat("Party > Alex: !dt", inSafari = false, self = "Me")
        SafariDowntime.chat("Bob has left the party.", inSafari = false, self = "Me")
        assertTrue(SafariDowntime.holding)
    }
    @Test fun `disbanding, leaving or joining another party drops every request`() {
        listOf("The party was disbanded because all invites expired and the party was empty.",
            "[MVP++] Farmer has disbanded the party!", "You left the party.",
            "You have been kicked from the party by [MVP+] Farmer", "You have joined [MVP+] Steve's party!").forEach { line ->
            SafariDowntime.chat("Party > Farmer: !dt", inSafari = false, self = "Me")
            SafariDowntime.chat("Party > Steve: !dt", inSafari = true, self = "Me")
            assertTrue(SafariDowntime.holding)
            SafariDowntime.chat(line, inSafari = false, self = "Me")
            assertFalse(SafariDowntime.holding, line)
            assertEquals("", SafariDowntime.holders(), line)
        }
    }
    @Test fun `turning the setting off releases an active hold and drops banked requests`() {
        SafariDowntime.chat("Party > Farmer: !dt", inSafari = false, self = "Me")
        assertTrue(SafariDowntime.holding)
        ConfigManager.downtimeCommand = false
        assertFalse(SafariDowntime.holding, "an active hold stops blocking as soon as the setting is off")
        SafariDowntime.chat("Party > Someone: hi", inSafari = false, self = "Me")
        ConfigManager.downtimeCommand = true
        assertFalse(SafariDowntime.holding, "it does not come back when the setting is turned on again")
        SafariDowntime.chat("Party > Farmer: !dt", inSafari = true, self = "Me")
        ConfigManager.downtimeCommand = false
        assertNull(SafariDowntime.leftSafari("Me"), "the run ending while off does not start a hold")
        ConfigManager.downtimeCommand = true
        assertNull(SafariDowntime.leftSafari("Me"), "and the banked request is gone, not saved for later")
        assertFalse(SafariDowntime.holding)
        // r is ignored while off, so the hold cannot be left half-released either.
        SafariDowntime.chat("Party > Farmer: !dt", inSafari = false, self = "Me")
        ConfigManager.downtimeCommand = false
        assertNull(SafariDowntime.chat("Party > Farmer: r", inSafari = false, self = "Me"))
        ConfigManager.downtimeCommand = true
        assertFalse(SafariDowntime.holding)
    }
    @Test fun `only party chat counts`() {
        assertNull(SafariDowntime.chat("Guild > Farmer: !dt", inSafari = false, self = "Farmer"))
        assertNull(SafariDowntime.chat("Party > Farmer: !dtx", inSafari = false, self = "Farmer"))
        assertFalse(SafariDowntime.holding)
    }
}
