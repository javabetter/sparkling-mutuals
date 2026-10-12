package net.johnceo.sparklingmutuals

import net.johnceo.sparklingmutuals.commands.PartyCommand
import net.johnceo.sparklingmutuals.commands.PartyCommandKind
import net.johnceo.sparklingmutuals.commands.SafariMissingReply
import net.johnceo.sparklingmutuals.safari.SafariBiome
import net.johnceo.sparklingmutuals.safari.SafariLedger
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MissingRunCommandTest {
    private fun parse(line: String) = PartyCommand.fromChat(line)?.command

    @Test fun `bare m asks for every biome and a letter or name asks for one`() {
        assertEquals(PartyCommand(PartyCommandKind.MISSING_RUN), parse("Party > [MVP+] Farmer: !m"))
        mapOf("c" to SafariBiome.CAVERN, "F" to SafariBiome.FOREST, "ice" to SafariBiome.ICY, "Haunted" to SafariBiome.HAUNTED)
            .forEach { (word, biome) -> assertEquals(biome, parse("Party > Farmer: !m $word")?.biome, word) }
        assertEquals("!m icy", parse("Party > Farmer: !M I")?.text)
    }
    @Test fun `other words and player lookups are not run queries`() {
        assertNull(parse("Party > Farmer: !m everything"))
        assertNull(parse("Party > Farmer: !m h extra"))
        assertNull(parse("Guild > Farmer: !m h"))
        assertEquals(PartyCommandKind.MISSING, parse("Party > Farmer: !missing JohnCEO")?.kind)
    }
    @Test fun `only clients inside the Safari answer`() {
        val command = PartyCommand(PartyCommandKind.MISSING_RUN, biome = SafariBiome.ICY)
        assertTrue(command.answerable(inSafari = true))
        assertFalse(command.answerable(inSafari = false))
        assertTrue(PartyCommand(PartyCommandKind.MUTUALS).answerable(inSafari = false))
    }
    @Test fun `a delayed reply is dropped once its run ends or another run starts`() {
        val command = PartyCommand(PartyCommandKind.MISSING_RUN)
        val ledger = SafariLedger().apply { enter(1_000) }
        val asked = ledger.current
        // While the election and lookup wait, the same run inside the Safari still gets its answer.
        assertTrue(SafariMissingReply.holds(command, asked, ledger.current, inSafari = true))
        assertFalse(SafariMissingReply.holds(command, asked, ledger.current, inSafari = false))
        ledger.leave(2_000)
        assertFalse(SafariMissingReply.holds(command, asked, ledger.current, inSafari = true))
        ledger.enter(3_000)
        assertFalse(SafariMissingReply.holds(command, asked, ledger.current, inSafari = true))
        // Asked outside any run: there is nothing to answer for.
        assertFalse(SafariMissingReply.holds(command, null, null, inSafari = true))
        // Commands that are not about the run are never bound to one.
        assertTrue(SafariMissingReply.holds(PartyCommand(PartyCommandKind.MUTUALS), asked, null, inSafari = false))
    }
    @Test fun `replies list what is left and mark finished biomes done`() {
        val left = mapOf(SafariBiome.CAVERN to listOf("Snoozle", "Rockmite"), SafariBiome.HAUNTED to listOf("Gazer"))
        val missing = { b: SafariBiome -> left[b].orEmpty() }
        assertEquals("Missing Cavern: Snoozle, Rockmite", SafariMissingReply.format(SafariBiome.CAVERN, missing))
        assertEquals("Missing Icy: Done", SafariMissingReply.format(SafariBiome.ICY, missing))
        assertEquals("Missing: Forest: Done | Cavern: Snoozle, Rockmite | Icy: Done | Haunted: Gazer",
            SafariMissingReply.format(null, missing))
    }
    @Test fun `replies are recognised so other clients do not repeat them`() {
        val command = PartyCommand(PartyCommandKind.MISSING_RUN, biome = SafariBiome.HAUNTED)
        assertTrue(command.matchesResponse("Missing Haunted: Gazer"))
        assertFalse(command.matchesResponse("Missing Icy: Done"))
        assertTrue(PartyCommand(PartyCommandKind.MISSING_RUN).matchesResponse("Missing: Forest: Done | Cavern: Done | Icy: Done | Haunted: Done"))
    }
}
