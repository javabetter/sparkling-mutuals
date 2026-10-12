package net.johnceo.sparklingmutuals.api

import net.johnceo.sparklingmutuals.config.ConfigManager

/** Immutable lookup identity. Credentials stay in memory and are omitted from diagnostics. */
data class ApiProvider private constructor(val key: String, val cloud: Boolean) {
    val enabled get() = key.isNotEmpty() || cloud
    override fun toString() = "ApiProvider(cloud=$cloud, enabled=$enabled)"
    companion object {
        fun capture(key: String = ConfigManager.apiKey, cloud: Boolean = ConfigManager.useCloudApi): ApiProvider {
            val trimmed = key.trim()
            return ApiProvider(trimmed, trimmed.isEmpty() && cloud)
        }
    }
}
