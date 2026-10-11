package net.johnceo.sparklingmutuals

import net.johnceo.sparklingmutuals.api.ApiRetryWindow
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ApiRetryWindowTest {
    @Test fun `successful operations do not cancel unrelated failed functions`() {
        val tickets = ApiRetryWindow(); val discoveries = ApiRetryWindow()
        tickets.failed(0, "key"); discoveries.failed(1000, "key")
        tickets.reset()
        assertFalse(tickets.due(181000, "key"))
        assertTrue(discoveries.due(181000, "key"))
    }
    @Test fun `failure retries at three minutes and reschedules from the latest failure`() {
        val retry = ApiRetryWindow()
        assertFalse(retry.pending)
        retry.failed(1000, "old")
        assertFalse(retry.due(180999, "old"))
        assertTrue(retry.due(181000, "old"))
        retry.failed(181500, "old")
        assertFalse(retry.due(361499, "old"))
        assertTrue(retry.due(361500, "old"))
    }
    @Test fun `key updates wake only failed operations and reset cancels retries`() {
        val retry = ApiRetryWindow()
        assertFalse(retry.due(0, "new"))
        retry.failed(1000, "expired")
        assertTrue(retry.due(1001, "replacement"))
        retry.reset()
        assertFalse(retry.pending)
        assertFalse(retry.due(999999, "replacement"))
        retry.failed(1000, "")
        assertTrue(retry.due(1001, "configured"))
    }
}
