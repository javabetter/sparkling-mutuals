package net.johnceo.sparklingmutuals.api

/** Client-thread retry timing; credentials stay in memory and are never logged. */
class ApiRetryWindow {
    private var failedProvider: ApiProvider? = null
    private var retryAt = 0L
    val pending get() = failedProvider != null
    fun failed(now: Long, key: String) = failed(now, ApiProvider.capture(key))
    fun failed(now: Long, provider: ApiProvider) { failedProvider = provider; retryAt = now + 180_000 }
    fun due(now: Long, key: String) = due(now, ApiProvider.capture(key))
    fun due(now: Long, provider: ApiProvider) = pending && (provider != failedProvider || now >= retryAt)
    fun reset() { failedProvider = null; retryAt = 0 }
}
