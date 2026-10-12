package net.johnceo.sparklingmutuals

import net.johnceo.sparklingmutuals.api.ApiRetryWindow
import net.johnceo.sparklingmutuals.api.ApiProvider
import net.johnceo.sparklingmutuals.config.ConfigManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ApiRetryWindowTest {
    @Test fun `captured provider remains stable across settings changes without exposing credentials`() {
        val cloud = ApiProvider.capture("", true)
        val disabled = ApiProvider.capture("", false)
        val direct = ApiProvider.capture("test-credential", true)
        assertNotEquals(cloud, disabled)
        assertTrue(cloud.enabled)
        assertFalse(disabled.enabled)
        assertEquals(direct, ApiProvider.capture("test-credential", false))
        assertFalse(direct.toString().contains("test-credential"))
        val retry = ApiRetryWindow()
        retry.failed(1000, disabled)
        assertFalse(retry.due(1001, disabled))
        assertTrue(retry.due(1001, cloud))
    }
    @Test fun `enabling cloud wakes a failed lookup with the same blank key`() {
        val saved = ConfigManager.useCloudApi
        try {
            ConfigManager.useCloudApi = false
            val retry = ApiRetryWindow()
            retry.failed(1000, "")
            assertFalse(retry.due(1001, ""))
            ConfigManager.useCloudApi = true
            assertTrue(retry.due(1001, ""))
        } finally { ConfigManager.useCloudApi = saved }
    }
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
