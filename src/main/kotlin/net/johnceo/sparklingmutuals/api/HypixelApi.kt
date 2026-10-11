package net.johnceo.sparklingmutuals.api

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.johnceo.sparklingmutuals.config.ConfigManager
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

object HypixelApi {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    fun getCurrentProfileUuid(uuid: String): String = data {
        val profiles = get("https://api.hypixel.net/v2/skyblock/profiles?uuid=$uuid")
            .getAsJsonArray("profiles") ?: throw ApiFailure.dataUnavailable()
        profiles.firstOrNull { it.asJsonObject.get("selected")?.asBoolean == true }
            ?.asJsonObject?.get("profile_id")?.asString ?: throw ApiFailure.dataUnavailable()
    }

    fun getSparklingCritters(playerUuid: String, profileUuid: String): Set<String> = data {
        val safari = getMember(playerUuid, profileUuid).getAsJsonObject("safari")
            ?: throw ApiFailure.dataUnavailable()
        val discoveries = safari.getAsJsonArray("discovered_sparkling_critters")
            ?: throw ApiFailure.dataUnavailable()
        discoveries.map { it.asString }.toSet()
    }

    fun getPlayerUuid(username: String): String {
        val response = send(HttpRequest.newBuilder()
            .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/$username")))
        if (response.statusCode() == 204 || response.statusCode() == 404) {
            throw ApiFailure(ApiFailureKind.PLAYER_NOT_FOUND, "Minecraft player not found: $username")
        }
        if (response.statusCode() == 429) throw ApiFailure(ApiFailureKind.RATE_LIMIT,
            "Player lookup request limit reached. Try again shortly.")
        if (response.statusCode() != 200) throw ApiFailure.unavailable()
        return try { JsonParser.parseString(response.body()).asJsonObject.get("id").asString }
        catch (_: Exception) { throw ApiFailure.unavailable() }
    }

    fun getSafariTickets(playerUuid: String, profileUuid: String): Map<String, Long> = data {
        val tickets = getMember(playerUuid, profileUuid).getAsJsonObject("safari")
            ?.getAsJsonObject("tickets") ?: throw ApiFailure.dataUnavailable()
        listOf("basic", "economy", "premium", "first_class").associateWith { name ->
            tickets.get(name)?.takeUnless { it.isJsonNull }?.asLong ?: 0L
        }
    }

    /** Hive id -> when it refills, for hives looted in the last hour on any session. */
    fun getHoneyRefills(playerUuid: String): Map<String, Long> = data {
        val times = getMember(playerUuid, getCurrentProfileUuid(playerUuid)).getAsJsonObject("foraging")
            ?.getAsJsonObject("honey")?.getAsJsonObject("refill_times") ?: return@data emptyMap()
        times.entrySet().filter { it.value.isJsonPrimitive && it.value.asJsonPrimitive.isNumber }.associate { it.key to it.value.asLong }
    }

    private fun getMember(playerUuid: String, profileUuid: String): JsonObject = data {
        get("https://api.hypixel.net/v2/skyblock/profile?profile=$profileUuid")
            .getAsJsonObject("profile")?.getAsJsonObject("members")
            ?.getAsJsonObject(playerUuid.replace("-", "")) ?: throw ApiFailure.dataUnavailable()
    }

    private fun get(url: String): JsonObject {
        val key = ConfigManager.apiKey.trim()
        if (key.isEmpty()) throw ApiFailure(ApiFailureKind.MISSING_KEY,
            "No Hypixel API key. Use /apikey or settings.")
        // Keep credentials out of URLs and diagnostic messages.
        val response = send(HttpRequest.newBuilder().uri(URI.create(url)).header("API-Key", key))
        ApiFailure.fromResponse(response.statusCode(), response.body())?.let { throw it }
        return JsonParser.parseString(response.body()).asJsonObject
    }

    private fun send(builder: HttpRequest.Builder): HttpResponse<String> = try {
        client.send(builder.timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString())
    } catch (_: Exception) { throw ApiFailure.unavailable() }

    private fun <T> data(read: () -> T): T = try { read() }
        catch (failure: ApiFailure) { throw failure }
        catch (_: Exception) { throw ApiFailure.dataUnavailable() }
}
