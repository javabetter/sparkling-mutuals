package net.johnceo.sparklingmutuals.commands

import net.johnceo.sparklingmutuals.api.ApiFailure
import net.johnceo.sparklingmutuals.config.ConfigManager

/** A failed lookup has a local message only, so another client can still provide the result. */
data class PartyLookupResult(val text: String, val successful: Boolean) {
    val partyMessage get() = text.takeIf { successful }
    companion object {
        fun fetch(command: PartyCommand, key: String, cloud: Boolean = ConfigManager.useCloudApi, lookup: () -> String): PartyLookupResult {
            if (command.requiresApiKey && key.isBlank() && !cloud) return PartyLookupResult("[SM] No Hypixel API key. Use /apikey or settings.", false)
            return try { PartyLookupResult(lookup(), true) }
            catch (failure: ApiFailure) { PartyLookupResult("[SM] ${failure.message.orEmpty()}", false) }
            catch (_: Exception) { PartyLookupResult("[SM] Lookup failed. Try again.", false) }
        }
    }
}

/** Ordered server party chat; the cursor separates old results from the current request. */
class PartyResponseHistory {
    private data class Entry(val id: Long, val text: String, val senderUuid: String?)
    private val messages = ArrayDeque<Entry>()
    var cursor = 0L
        private set
    fun record(body: String, senderUuid: String?) {
        messages.addLast(Entry(++cursor, body, senderUuid?.lowercase()))
        while (messages.size > 200) messages.removeFirst()
    }
    fun hasReply(command: PartyCommand, after: Long, roster: List<String>) = messages.any {
        it.id > after && (it.senderUuid == null || roster.any { id -> id.equals(it.senderUuid, true) }) && command.matchesResponse(it.text)
    }
    fun containsResult(result: String, after: Long, roster: List<String>) = messages.any {
        it.id > after && (it.senderUuid == null || roster.any { id -> id.equals(it.senderUuid, true) }) &&
            result.isNotEmpty() && it.text.contains(result)
    }
    fun clear() { messages.clear(); cursor = 0 }
}
