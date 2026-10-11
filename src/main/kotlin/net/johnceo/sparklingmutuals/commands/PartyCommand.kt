package net.johnceo.sparklingmutuals.commands

import java.security.MessageDigest
import net.johnceo.sparklingmutuals.safari.SafariRoster
import net.johnceo.sparklingmutuals.safari.SafariMode
import net.johnceo.sparklingmutuals.safari.SafariFullClear

data class PartyChat(val sender: String, val body: String) {
    companion object {
        private val envelope = Regex("""^Party\s*>\s*(?:\[[^\]]+]\s*)*([A-Za-z0-9_]{1,16}):\s*(.*)$""")
        fun parse(text: String): PartyChat? {
            val match = envelope.matchEntire(text.replace(Regex("§."), "").trim()) ?: return null
            return PartyChat(match.groupValues[1], match.groupValues[2].trim())
        }
    }
}

enum class PartyCommandKind { MUTUALS, MISSING, TICKETS, HELP, PB_DOOM, PB_WUMPA, PB_FOREST, PB_HAUNTED, PB_ICY, PB_CAVERN }

data class PartyCommand(val kind: PartyCommandKind, val ign: String = "", val duo: Boolean = false, val trackedPlayer: Boolean = false) {
    val requiresApiKey get() = kind in listOf(PartyCommandKind.MUTUALS, PartyCommandKind.MISSING, PartyCommandKind.TICKETS)
    val pbName get() = when (kind) {
        PartyCommandKind.PB_DOOM -> "Doomspiral"
        PartyCommandKind.PB_WUMPA -> "Wumpa"
        PartyCommandKind.PB_FOREST -> "Forest"
        PartyCommandKind.PB_HAUNTED -> "Haunted"
        PartyCommandKind.PB_ICY -> "Icy"
        PartyCommandKind.PB_CAVERN -> "Cavern"
        else -> null
    }
    fun allowed(mode: SafariMode = SafariFullClear.mode) = when {
        pbName == null -> true
        kind == PartyCommandKind.PB_DOOM || kind == PartyCommandKind.PB_WUMPA -> mode != SafariMode.FULL_CLEAR
        else -> mode != SafariMode.SPARKLING
    }
    // Unnamed PB requests target this client; explicit player queries retain their requested IGN.
    fun forResponder(name: String) = if (pbName != null && !trackedPlayer)
        copy(ign = name) else this
    private val failurePrefix: String get() = when (kind) {
        PartyCommandKind.MUTUALS -> "Mutual Timesave Sparkling Critters: "
        PartyCommandKind.MISSING -> "Missing Timesave Sparklings for $ign: "
        PartyCommandKind.TICKETS -> "Safari Tickets for $ign: "
        PartyCommandKind.HELP -> "Commands: "
        PartyCommandKind.PB_DOOM -> "Doomspiral PB: "
        else -> "$pbName PB: "
    }

    fun failureResult(message: String): String = failurePrefix + message

    fun matchesResponse(body: String): Boolean {
        return when (kind) {
            PartyCommandKind.MISSING, PartyCommandKind.MUTUALS -> {
                val prefixes = if (kind == PartyCommandKind.MISSING)
                    listOf("Missing Timesave Sparklings for $ign: ", "Missing Sparklings for $ign: ")
                else listOf("Mutual Timesave Sparkling Critters: ", "Mutual Sparkling Critters: ")
                prefixes.any { prefix ->
                    if (body.startsWith(prefix, true)) lookupResult.matches(body.substring(prefix.length))
                    else {
                        val chunk = Regex("^${Regex.escape(prefix.removeSuffix(": "))} \\[([1-9][0-9]*)/([1-9][0-9]*)\\]: (.+)$", RegexOption.IGNORE_CASE).matchEntire(body)
                        chunk != null && (chunk.groupValues[1].toIntOrNull() ?: Int.MAX_VALUE) <=
                            (chunk.groupValues[2].toIntOrNull() ?: 0) && lookupResult.matches(chunk.groupValues[3])
                    }
                }
            }
            PartyCommandKind.TICKETS -> Regex(
                "^${Regex.escape(ign)}: Basic \\(\\d+\\), Economy \\(\\d+\\), Premium \\(\\d+\\), First-Class \\(\\d+\\)$",
                RegexOption.IGNORE_CASE
            ).matches(body)
            PartyCommandKind.HELP -> body.startsWith("[SM] Commands: ")
            else -> Regex(
                "^${if (ign.isEmpty()) "[A-Za-z0-9_]{1,16}" else Regex.escape(ign)}'s ${pbName}${if (trackedPlayer) " Observed" else if (duo) " Duo" else ""}${if (kind in listOf(PartyCommandKind.PB_FOREST, PartyCommandKind.PB_HAUNTED, PartyCommandKind.PB_ICY, PartyCommandKind.PB_CAVERN)) "(?: (?:Full Clear|Unique Run))?" else ""} PB: (?:\\d+:\\d{2}\\.\\d{3}|Not recorded yet)(?: \\| Party PB: \\d+:\\d{2}\\.\\d{3})?$",
                RegexOption.IGNORE_CASE).matches(body)
        }
    }

    val text: String get() = when (kind) {
        PartyCommandKind.MUTUALS -> "!mutuals"
        PartyCommandKind.MISSING -> "!missing $ign"
        PartyCommandKind.TICKETS -> "!tickets $ign"
        PartyCommandKind.HELP -> "!commands"
        PartyCommandKind.PB_DOOM -> "!pb doom${if (trackedPlayer) " $ign" else if (duo) " duo" else ""}"
        PartyCommandKind.PB_WUMPA -> "!pb wumpa${if (trackedPlayer) " $ign" else if (duo) " duo" else ""}"
        else -> "!pb ${pbName!!.lowercase()}"
    }

    companion object {
        private val names = (SafariRoster.all.map { Regex.escape(it.name) } + "All Birds").joinToString("|")
        private val lookupResult = Regex("^(?:None|None\\. :skull:|All\\. :skull:|(?:$names)(?:, (?:$names))*)$", RegexOption.IGNORE_CASE)
        private val bossLookup = Regex("""^!pb\s+(doom|wumpa)\s+([A-Za-z0-9_]{1,16})$""", RegexOption.IGNORE_CASE)
        private val lookup = Regex("""^!(missing|tickets?)\s+([A-Za-z0-9_]{1,16})$""", RegexOption.IGNORE_CASE)
        fun fromChat(text: String): PartyRequest? {
            val chat = PartyChat.parse(text) ?: return null
            val command = when (chat.body.lowercase()) {
                "!mutual", "!mutuals" -> PartyCommand(PartyCommandKind.MUTUALS)
                "!commands" -> PartyCommand(PartyCommandKind.HELP)
                "!pb doom" -> PartyCommand(PartyCommandKind.PB_DOOM)
                "!pb doom duo" -> PartyCommand(PartyCommandKind.PB_DOOM, duo = true)
                "!pb wumpa" -> PartyCommand(PartyCommandKind.PB_WUMPA)
                "!pb wumpa duo" -> PartyCommand(PartyCommandKind.PB_WUMPA, duo = true)
                "!pb forest" -> PartyCommand(PartyCommandKind.PB_FOREST)
                "!pb haunted" -> PartyCommand(PartyCommandKind.PB_HAUNTED)
                "!pb icy" -> PartyCommand(PartyCommandKind.PB_ICY)
                "!pb cavern" -> PartyCommand(PartyCommandKind.PB_CAVERN)
                else -> {
                    val boss = bossLookup.matchEntire(chat.body)
                    if (boss != null) PartyCommand(if (boss.groupValues[1].equals("doom", true))
                        PartyCommandKind.PB_DOOM else PartyCommandKind.PB_WUMPA, boss.groupValues[2], trackedPlayer = true)
                    else {
                        val match = lookup.matchEntire(chat.body) ?: return null
                        PartyCommand(if (match.groupValues[1].equals("missing", true))
                            PartyCommandKind.MISSING else PartyCommandKind.TICKETS, match.groupValues[2])
                    }
                }
            }
            return PartyRequest(chat.sender, command)
        }
    }
}

data class PartyRequest(val sender: String, val command: PartyCommand) {
    val token: String = MessageDigest.getInstance("SHA-256")
        .digest("${sender.lowercase()}:${command.text.lowercase()}".toByteArray(Charsets.UTF_8))
        .take(6).joinToString("") { "%02x".format(it.toInt() and 255) }
}
