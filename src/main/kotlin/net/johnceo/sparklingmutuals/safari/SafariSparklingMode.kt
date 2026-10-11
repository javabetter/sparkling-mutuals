package net.johnceo.sparklingmutuals.safari

import net.johnceo.sparklingmutuals.LocalChat

import net.johnceo.sparklingmutuals.api.ApiFailure
import net.johnceo.sparklingmutuals.api.ApiRetryWindow
import net.johnceo.sparklingmutuals.commands.SafariLookup
import net.johnceo.sparklingmutuals.config.ConfigManager
import net.johnceo.sparklingmutuals.party.PartyManager
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** A partial or different-party lookup must never claim a discovery is shared by everyone. */
class PartySparklingState {
    private var members = emptySet<String>()
    private var shared: Set<String>? = null
    val ready get() = shared != null
    fun select(roster: Set<String>) {
        if (members != roster) { members = roster.toSet(); shared = null }
    }
    fun accept(roster: Set<String>, discoveries: Map<String, Set<String>>): Boolean {
        if (roster.isEmpty() || roster != members || !discoveries.keys.containsAll(roster)) return false
        shared = roster.map { discoveries.getValue(it).map(::key).toSet() }.reduce(Set<String>::intersect)
        return true
    }
    private fun key(species: String) = species.uppercase(Locale.ROOT).replace(' ', '_')
    fun everyoneHas(vararg species: String) = shared?.containsAll(species.map(::key)) == true
    fun needs(species: String) = !everyoneHas(species)
    fun reset() { members = emptySet(); shared = null }
}

/** HMAPI roster refreshes run on the client thread; profile requests run on a daemon worker. */
object SafariSparklingMode {
    val state = PartySparklingState()
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "Sparkling mode lookups").apply { isDaemon = true } }
    private var task: Future<*>? = null
    private var generation = 0L
    private var roster = emptySet<String>()
    private var key = ""
    private var partyRefreshAt = 0L
    private var attemptedAt = 0L
    private var loadedRun: SafariRun? = null
    private var refresh = false
    private var warning: String? = null
    private val retry = ApiRetryWindow()
    fun requestRefresh() { invalidate(); refresh = true }
    private fun local(client: Minecraft, message: String) {
        if (warning == message) return
        warning = message
        client.player?.sendSystemMessage(LocalChat.component("[SM] $message"))
    }
    private fun invalidate() { generation++; task?.cancel(false); task = null; attemptedAt = 0; retry.reset() }
    fun tick(client: Minecraft) {
        if (client.player == null || client.connection == null) return
        val active = SafariAssist.location == SafariLocation.INSIDE || ConfigManager.sparklingMode
        val currentKey = ConfigManager.apiKey
        if (key != currentKey) {
            invalidate(); state.reset(); key = currentKey; refresh = true; warning = null
        }
        if (!active) return
        if (currentKey.isBlank() && !ConfigManager.useCloudApi) {
            if (ConfigManager.sparklingMode) local(client, "No Hypixel API key. Use /apikey or settings.")
            return
        }
        val now = System.currentTimeMillis()
        if (now - partyRefreshAt >= 60_000) {
            partyRefreshAt = now
            PartyManager.requestPartyInfo()
        }
        // A confirmed empty roster is solo. Unknown membership cannot be treated as solo.
        val members = PartyManager.withLocal(client.player!!.uuid.toString())
        if (members == null) {
            if (roster.isNotEmpty()) { invalidate(); roster = emptySet(); state.reset(); refresh = true }
            PartyManager.requestPartyInfo()
            return
        }
        if (members != roster) { invalidate(); roster = members; state.select(members); warning = null }
        val run = SafariTracking.ledger.current
        if (task != null || (!refresh && state.ready && loadedRun === run) ||
            if (retry.pending) !retry.due(now, currentKey) else now - attemptedAt < 60_000) return
        retry.reset()
        refresh = false
        attemptedAt = now
        val revision = generation
        val connection = client.connection
        task = executor.submit {
            val result = runCatching { members.associateWith { SafariLookup.discoveries(it, currentKey) } }
            client.execute {
                if (revision != generation || client.connection !== connection || ConfigManager.apiKey != currentKey ||
                    client.player?.uuid?.toString()?.let(PartyManager::withLocal) != members) return@execute
                task = null
                result.fold({ discoveries ->
                    if (state.accept(members, discoveries)) { loadedRun = run; warning = null }
                }, { failure ->
                    retry.failed(System.currentTimeMillis(), currentKey); refresh = true
                    local(client, (failure as? ApiFailure)?.message ?: ApiFailure.unavailable().message!!)
                })
            }
        }
    }
    fun reset() {
        invalidate(); state.reset(); roster = emptySet(); key = ""; partyRefreshAt = 0
        loadedRun = null; refresh = false; warning = null
    }
}
