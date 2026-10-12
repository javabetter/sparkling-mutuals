package net.johnceo.sparklingmutuals

import net.johnceo.sparklingmutuals.safari.SafariIsland
import net.johnceo.sparklingmutuals.safari.SafariManagerGuard
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SafariManagerGuardTest {
    @Test fun `a kick called in party chat holds the manager for thirty seconds`() {
        SafariManagerGuard.reset()
        SafariManagerGuard.chat("Party > [MVP+] Farmer: kick him he's afk", 1_000)
        assertTrue(SafariManagerGuard.holding(1_000 + SafariManagerGuard.HOLD_MILLIS - 1))
        assertFalse(SafariManagerGuard.holding(1_000 + SafariManagerGuard.HOLD_MILLIS))
    }
    @Test fun `only party chat with kick as its own word counts`() {
        assertTrue(SafariManagerGuard.callsKick("Party > Farmer: KICK"))
        assertTrue(SafariManagerGuard.callsKick("Party > [VIP] Farmer: can someone kick Steve?"))
        assertFalse(SafariManagerGuard.callsKick("Party > Farmer: kickstart the run"))
        assertFalse(SafariManagerGuard.callsKick("Guild > Farmer: kick him"))
        assertFalse(SafariManagerGuard.callsKick("Farmer has been kicked from the party"))
    }
    @Test fun `only the Safari island counts, not Torrhus Canyon's Critter Safari zone`() {
        assertTrue(SafariIsland.isSafari("safari"))
        assertFalse(SafariIsland.isSafari("foraging_3"))
        assertFalse(SafariIsland.isSafari(null))
    }
}
