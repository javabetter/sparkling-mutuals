package net.johnceo.sparklingmutuals.api

/** Client-thread retry timing; credentials stay in memory and are never logged. */
class ApiRetryWindow {
    private var failedKey: String? = null
    private var retryAt = 0L
    val pending get() = failedKey != null
    fun failed(now: Long, key: String) { failedKey = key; retryAt = now + 180_000 }
    fun due(now: Long, key: String) = pending && (key != failedKey || now >= retryAt)
    fun reset() { failedKey = null; retryAt = 0 }
}
