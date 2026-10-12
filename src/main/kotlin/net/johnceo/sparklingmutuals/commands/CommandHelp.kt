package net.johnceo.sparklingmutuals.commands

import net.johnceo.sparklingmutuals.safari.SafariMode
import net.johnceo.sparklingmutuals.safari.SafariFullClear

/** Help lists actual commands available in the selected mode. */
object CommandHelp {
    private val party = listOf(
        PartyCommandKind.MUTUALS to "Shared sparkling discoveries",
        PartyCommandKind.MISSING to "Missing sparkling discoveries",
        PartyCommandKind.TICKETS to "Safari ticket counts",
        PartyCommandKind.PB_DOOM to "Solo Doomspiral PB (add duo or a player name)",
        PartyCommandKind.PB_WUMPA to "Solo Wumpa PB (add duo or a player name)",
        PartyCommandKind.PB_FOREST to "Forest biome PB",
        PartyCommandKind.PB_CAVERN to "Cavern biome PB",
        PartyCommandKind.PB_ICY to "Icy biome PB",
        PartyCommandKind.PB_HAUNTED to "Haunted biome PB",
        PartyCommandKind.MISSING_RUN to "What's left this run (add c/f/i/h for one biome)",
        PartyCommandKind.HELP to "Command list"
    )
    private val local = listOf(
        "/sm" to "Settings",
        "/sm gui" to "Move and resize HUDs",
        "/captures" to "Current or last run captures",
        "/sm debug" to "Save entity diagnostics",
        "/fc" to "Full clear mode",
        "/unique" to "Unique run mode",
        "/sparkle" to "Sparkling run mode",
        "/alert" to "Toggle warp reminders",
        "/alertdelay <seconds>" to "Warp reminder delay",
        "/apikey <key>" to "Set API key"
    )
    /** Party chat words other mods' users can say that this mod reacts to, without a lookup reply. */
    private val partyWords = listOf("!dt [reason]" to "Downtime after this run; say r when ready")
    private fun available(mode: SafariMode) = party.map { (kind, description) -> PartyCommand(kind, if (kind in listOf(PartyCommandKind.MISSING, PartyCommandKind.TICKETS)) "<IGN>" else "") to description }
        .filter { it.first.allowed(mode) }
    fun localLines(mode: SafariMode = SafariFullClear.mode) = listOf("§6Sparkling Mutuals — Commands", "§eParty chat commands") +
        available(mode).map { (command, description) -> "§6${command.text} §7— $description" } +
        partyWords.map { (command, description) -> "§6$command §7— $description" } +
        listOf("§eLocal commands") + local.map { (command, description) -> "§6$command §7— $description" } +
        listOf("§7<IGN> = Minecraft username. Party commands use /pc.")
    fun partyReply(mode: SafariMode = SafariFullClear.mode) = "[SM] Commands: Party: " +
        available(mode).joinToString(", ") { it.first.text } + if (mode != SafariMode.FULL_CLEAR) ", !pb doom duo, !pb wumpa duo, !pb doom <IGN>, !pb wumpa <IGN>" else ""
}
