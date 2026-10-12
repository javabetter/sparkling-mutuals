package net.johnceo.sparklingmutuals.commands

import com.mojang.brigadier.arguments.StringArgumentType
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.johnceo.sparklingmutuals.LocalChat
import net.johnceo.sparklingmutuals.api.ApiFailure
import net.johnceo.sparklingmutuals.api.HypixelApi
import net.johnceo.sparklingmutuals.api.SafariProfile
import net.johnceo.sparklingmutuals.party.PartyManager
import net.johnceo.sparklingmutuals.safari.SafariBiome
import net.johnceo.sparklingmutuals.safari.SafariRoster
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.MutableComponent
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.Executors

/**
 * `/sparkling` — a player's Safari card: hunting level, sparkling discoveries, tickets and essence, with every line
 * carrying a hover that lays out caught and missing sparklings per biome. `/sparkling <name>` for someone else,
 * `/sparkling <a> <b> ...` or `/sparkling party` for the sparklings all of them share.
 */
object SparklingLookup {
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "Sparkling Mutuals /sparkling").apply { isDaemon = true } }
    /** Hovers that carry their own layout and must not be word-wrapped (see SparklingHoverMixin). */
    private val wide = Collections.newSetFromMap(Collections.synchronizedMap(WeakHashMap<Any, Boolean>()))
    fun isWide(hover: Any?) = hover != null && hover in wide

    fun register() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(ClientCommands.literal("sparkling")
                .executes { Minecraft.getInstance().player?.name?.string?.let { card(it) }; 1 }
                .then(ClientCommands.argument("players", StringArgumentType.greedyString()).executes {
                    val names = StringArgumentType.getString(it, "players").trim().split(Regex("\\s+")).filter(String::isNotEmpty)
                    when {
                        names.size == 1 && names[0].equals("party", true) -> party()
                        names.size == 1 -> card(names[0])
                        else -> compare(names.distinctBy(String::lowercase))
                    }
                    1
                }))
        }
    }

    private fun tell(text: String) { Minecraft.getInstance().player?.sendSystemMessage(LocalChat.component(text)) }
    private fun failure(error: Throwable) = (error as? ApiFailure)?.message ?: "Lookup failed. Try again."

    private fun card(name: String) {
        val client = Minecraft.getInstance()
        tell("[SM] Looking up $name...")
        executor.submit {
            val result = runCatching { HypixelApi.getSafariProfile(HypixelApi.getPlayerUuid(name)) }
            client.execute {
                result.onFailure { tell("[SM] ${failure(it)}") }.onSuccess { show(name, it) }
            }
        }
    }

    private fun show(name: String, profile: SafariProfile) {
        val player = Minecraft.getInstance().player ?: return
        val hover = hover(profile.caught, profile.missing)
        wide.add(hover)
        val lines = buildList {
            add("§6§l$name")
            add("§7Hunting level §f${profile.huntingLevel?.let { if (it > 60) "%.2f".format(it) else it.toInt().toString() } ?: "?"}")
            add("§f${profile.caught.size}§7/${SafariRoster.all.size} sparklings §8(hover to expand)")
            profile.totalCaptured?.let { total ->
                val repeats = maxOf(0L, total - profile.caught.size)
                add("§7Total sparklings: §f$total §8($repeats duplicate${if (repeats == 1L) "" else "s"})")
            }
            add("§7Tickets: " + SafariProfile.TICKET_TIERS.zip(listOf("§a", "§9", "§5", "§6"))
                .joinToString(" §8| ") { (tier, colour) -> "$colour${profile.tickets[tier] ?: 0}" } +
                " §8| §b(${profile.tickets.values.sum()})")
            profile.essence?.let { add("§7Safari Essence: §d" + "%,d".format(it)) }
        }
        val rule = Component.literal("                    ").withStyle(ChatFormatting.GOLD, ChatFormatting.STRIKETHROUGH)
        player.sendSystemMessage(rule)
        // One message per line, each with the hover, so it's one hover away wherever the cursor is.
        lines.forEach { line -> player.sendSystemMessage(Component.literal(line).withStyle { it.withHoverEvent(HoverEvent.ShowText(hover)) }) }
        player.sendSystemMessage(rule)
    }

    private fun compare(names: List<String>) {
        val client = Minecraft.getInstance()
        tell("[SM] Looking up ${names.joinToString(", ")}...")
        executor.submit {
            val results = names.associateWith { runCatching { HypixelApi.getSafariProfile(HypixelApi.getPlayerUuid(it)) } }
            client.execute { report(results) }
        }
    }

    private fun party() {
        val client = Minecraft.getInstance()
        val local = client.player?.uuid?.toString() ?: return
        PartyManager.withPartyInfo {
            val roster = PartyManager.withLocal(local)?.toList() ?: return@withPartyInfo tell("[SM] Party not known yet. Try again.")
            if (roster.size < 2) return@withPartyInfo tell("[SM] You're not in a party.")
            fun nameOf(uuid: String) = client.connection?.getPlayerInfo(java.util.UUID.fromString(uuid))?.profile?.name ?: uuid.take(8)
            val named = roster.associateBy(::nameOf)
            tell("[SM] Looking up ${named.keys.joinToString(", ")}...")
            executor.submit {
                val results = named.mapValues { (_, uuid) -> runCatching { HypixelApi.getSafariProfile(uuid) } }
                client.execute { report(results) }
            }
        }
    }

    private fun report(results: Map<String, Result<SafariProfile>>) {
        val found = results.mapNotNull { (name, result) -> result.getOrNull()?.let { name to it } }.toMap()
        found.forEach { (name, profile) -> tell("[SM] §f$name §7- §f${profile.caught.size}§7/${SafariRoster.all.size} sparklings") }
        val failed = results.filterValues { it.isFailure }.keys
        if (failed.isNotEmpty()) tell("[SM] §cCouldn't look up: §f${failed.joinToString(", ")}")
        if (found.size < 2) return
        val shared = shared(found.values.map { it.caught })
        tell("[SM] Sparklings all ${found.size} have (${shared.size}): " + if (shared.isEmpty()) "§8none" else "§f" + shared.joinToString(", "))
    }

    /** Critters every list contains, in roster order. */
    fun shared(caught: List<List<String>>): List<String> =
        SafariRoster.all.map { it.name }.filter { name -> caught.all { name in it } }

    // ------------------------------------------------------------------ the hover

    /** Rarity from the roster colour: common (white), uncommon, rare, epic, legendary. */
    fun rarity(name: String) = when (SafariRoster.color(name)) {
        0xFF55FF55.toInt() -> 1; 0xFF5555FF.toInt() -> 2; 0xFFAA00AA.toInt() -> 3; 0xFFFFAA00.toInt() -> 4; else -> 0
    }
    /** One column per biome, most common first and rarest last, alphabetical within a rarity. */
    fun columns(critters: Collection<String>): List<List<String>> = SafariBiome.entries.map { biome ->
        critters.filter { SafariRoster.named(it)?.biome == biome }.sortedWith(compareBy(::rarity, String::lowercase))
    }

    private const val SEPARATOR = " §8| "
    private fun hover(caught: List<String>, missing: List<String>): MutableComponent {
        val font = Minecraft.getInstance().font
        val tables = listOf(caught, missing).map { table(columns(it)) }
        val width = tables.maxOf { it.second }
        fun rule(c: Char) = Component.literal(c.toString().repeat(maxOf(4, width / maxOf(1, font.width(c.toString())))) + "\n")
            .withStyle(ChatFormatting.DARK_GRAY)
        return Component.empty()
            .append(Component.literal("Sparklings caught\n\n").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD))
            .append(tables[0].first.invoke(rule('='))).append(rule('-'))
            .append(Component.literal("Sparklings missing\n\n").withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
            .append(tables[1].first.invoke(rule('=')))
    }

    /**
     * A "Forest (5) | Cavern (3) | ..." header, underlined, with each column starting where its header does. Chat text
     * isn't monospaced, so cells are padded with spaces measured in the game's font. Returns the renderer and width.
     */
    private fun table(columns: List<List<String>>): Pair<(Component) -> Component, Int> {
        val font = Minecraft.getInstance().font
        val space = maxOf(1, font.width(" "))
        val separator = font.width(Component.literal(SEPARATOR))
        val starts = IntArray(columns.size)
        val headers = mutableListOf<Component>()
        var x = 0
        SafariBiome.entries.forEachIndexed { i, biome ->
            starts[i] = x
            var header: MutableComponent = Component.literal("${biome.label} (${columns[i].size})")
                .withStyle { it.withColor(biome.color and 0xFFFFFF).withBold(true).withUnderlined(true) }
            val cell = maxOf(font.width(header), columns[i].maxOfOrNull { font.width(it) } ?: 0)
            if (cell > font.width(header) && i < columns.size - 1)
                header = Component.empty().append(header).append(" ".repeat(Math.round((cell - font.width(header)).toFloat() / space)))
            headers += header
            x += font.width(header) + if (i < columns.size - 1) separator else 0
        }
        val render = { rule: Component ->
            val out = Component.empty()
            headers.forEachIndexed { i, h -> if (i > 0) out.append(SEPARATOR); out.append(h) }
            out.append("\n").append(rule)
            val rows = columns.maxOf { it.size }
            if (rows == 0) out.append(Component.literal("none\n").withStyle(ChatFormatting.GRAY))
            for (row in 0 until rows) {
                val line = Component.empty()
                var at = 0
                columns.forEachIndexed { i, column ->
                    val critter = column.getOrNull(row) ?: return@forEachIndexed
                    val pad = starts[i] - at
                    if (pad > 0) { val n = Math.round(pad.toFloat() / space); line.append(" ".repeat(n)); at += n * space }
                    val cell = Component.literal(critter).withStyle { it.withColor(SafariRoster.color(critter) and 0xFFFFFF) }
                    line.append(cell); at += font.width(cell)
                }
                out.append(line).append("\n")
            }
            out
        }
        return render to x
    }
}
