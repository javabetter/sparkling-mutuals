package net.johnceo.sparklingmutuals.api

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Shiny Hunter's shared API: a Cloudflare Worker that holds a Hypixel key, so lookups work without one of your own.
 * `/profile/<uuid>` returns `/v2/skyblock/profiles` cut down to the selected profile and that one member, in Hypixel's
 * own shape. The member keeps exactly these fields:
 *  - `safari` (whole: sparkling discoveries, tickets, ...)
 *  - `player_data.experience.SKILL_HUNTING`
 *  - `currencies.essence.SAFARI`
 *  - `foraging.honey` (whole: `refill_times`, ...)
 * A field Hypixel itself leaves out (a player who has never looted a hive has no `foraging.honey`) is left out here
 * too, never replaced by an empty value; readers must treat a missing field as unknown. `src/test/resources/cloud`
 * holds two responses produced by the Worker's own trimming code. The Worker limits requests per address and caches
 * each profile for a minute; so does this client.
 */
object CloudApi {
    const val URL = "https://shinyhunter-api.shinyhunter-api.workers.dev"
    /** The Worker only answers known clients. */
    const val AGENT = "Sparkling Mutuals"
    private const val CACHE_MILLIS = 60_000L
    private data class Cached(val body: JsonObject, val at: Long)
    private val cache = mutableMapOf<String, Cached>()
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    /** Profiles for a player, as Hypixel would return them for `profiles?uuid=`. */
    @Synchronized fun profiles(uuid: String): JsonObject {
        val id = uuid.replace("-", "").lowercase()
        val now = System.currentTimeMillis()
        cache[id]?.takeIf { now - it.at < CACHE_MILLIS }?.let { return it.body }
        val response = try {
            client.send(HttpRequest.newBuilder(URI.create("$URL/profile/$id")).timeout(Duration.ofSeconds(10))
                .header("User-Agent", AGENT).GET().build(), HttpResponse.BodyHandlers.ofString())
        } catch (_: Exception) { throw ApiFailure.unavailable() }
        failure(response.statusCode())?.let { throw it }
        val body = try { JsonParser.parseString(response.body()).asJsonObject } catch (_: Exception) { throw ApiFailure.unavailable() }
        cache[id] = Cached(body, now)
        cache.entries.removeIf { now - it.value.at > CACHE_MILLIS }
        return body
    }
    fun failure(status: Int): ApiFailure? = when (status) {
        200 -> null
        429 -> ApiFailure(ApiFailureKind.RATE_LIMIT, "Shiny Hunter API is busy. Try again shortly.")
        else -> ApiFailure.unavailable()
    }
    @Synchronized fun clearCache() = cache.clear()
}

/** Reading the shared API's profiles list; kept apart from CloudApi so it needs no HTTP client. */
object CloudProfiles {
    fun selectedProfileId(profiles: JsonObject): String? = profiles.getAsJsonArray("profiles")
        ?.map { it.asJsonObject }?.firstOrNull { it.get("selected")?.asBoolean == true }?.get("profile_id")?.asString
    fun member(profiles: JsonObject, playerUuid: String, profileUuid: String): JsonObject? = profiles.getAsJsonArray("profiles")
        ?.map { it.asJsonObject }?.firstOrNull { it.get("profile_id")?.asString == profileUuid }
        ?.getAsJsonObject("members")?.getAsJsonObject(playerUuid.replace("-", "").lowercase())
}
