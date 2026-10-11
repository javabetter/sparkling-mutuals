package net.johnceo.sparklingmutuals.history

import net.fabricmc.loader.api.FabricLoader
import net.johnceo.sparklingmutuals.LocalChat
import net.johnceo.sparklingmutuals.config.ConfigManager
import net.johnceo.sparklingmutuals.safari.SafariMessages
import net.johnceo.sparklingmutuals.safari.SafariRoster
import net.johnceo.sparklingmutuals.safari.SafariRules
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import java.io.BufferedReader
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.CompletableFuture
import java.util.zip.GZIPInputStream

/**
 * Rebuilds past runs and sparklings from the game's chat logs (the dated `.log.gz` archives and `latest.log`). A run starts at the
 * Safari Manager letting you in and ends when you warp out ("Sending to server ...") or the log ends. Biomes called done
 * in party chat ("Forest clear!", "icy done") give its splits. On each launch the newest archived logs are read too,
 * because Minecraft keeps only seven logs a day and deletes the oldest of a day on the eighth launch.
 */
object LogImporter {
    private val fileName = Regex("""^(\d{4})-(\d{2})-(\d{2})-(\d+)\.log\.gz$""")
    private val line = Regex("""^\[(\d{2}):(\d{2}):(\d{2})\] \[[^\]]*\]: \[CHAT\] (.*)$""")
    private const val MANAGER = "[NPC] Safari Manager: "
    private val usesTicket = setOf("Looks good to me. Have fun out there!", "I already saw your ticket, so you're free to go.")
    private const val ALREADY_GAVE = "You already gave me your ticket, bud."
    private val sending = Regex("""^Sending to server \S+\.\.\.$""")
    private val partyLine = Regex("""^Party > [^:]*?: (.+)$""")
    private val biomeDone = Regex("""^(cavern|forest|haunted|icy|ice) (?:clear|done)!?$""", RegexOption.IGNORE_CASE)

    class Result(val runs: List<RunRecord>, val sparklings: List<SparklingEntry>, val files: Int, val newest: Long)
    private data class LogFile(val path: Path, val day: LocalDate, val order: Int, val modified: Long)

    @Volatile var running = false
        private set
    @Volatile private var bytesTotal = 1L
    @Volatile private var bytesDone = 0L
    @Volatile private var filesTotal = 0
    @Volatile private var filesDone = 0
    private var showProgress = false
    private var autoDone = false
    private var progressTicks = 0

    fun defaultLogs(): Path = FabricLoader.getInstance().gameDir.resolve("logs")

    /** Parses log text; the pure part, shared by files and tests. */
    class Parser(private val self: String) {
        val runs = mutableListOf<RunRecord>()
        val sparklings = mutableListOf<SparklingEntry>()
        private var ticketsSinceSparkling = 0
        private var run: RunRecord? = null
        private var tally = RunTally()
        private var splits = linkedMapOf<String, Long>()
        private var lastSecured = 0L

        fun read(reader: BufferedReader, startDay: LocalDate) {
            var day = startDay
            var last = 0L
            reader.lineSequence().forEach { raw ->
                if ("[CHAT]" !in raw) return@forEach
                val m = line.matchEntire(raw) ?: return@forEach
                var at = day.atTime(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                if (last != 0L && at < last - 3_600_000L) { day = day.plusDays(1); at += 86_400_000L } // past midnight
                last = at
                handle(SafariRules.strip(m.groupValues[4]), at)
            }
            close(last)
        }

        fun handle(text: String, at: Long) {
            if (text.isEmpty()) return
            run?.let { current -> partyLine.matchEntire(text)?.let { split(current, it.groupValues[1].trim(), at); return } }
            if (SafariMessages.lines(text).isEmpty()) return // party, guild or private chat
            if (text.startsWith(MANAGER)) {
                val said = text.removePrefix(MANAGER).trim()
                val ticket = said in usesTicket
                if (ticket || said == ALREADY_GAVE) {
                    if (ticket) ticketsSinceSparkling++
                    if (run == null) { run = RunRecord(start = at, imported = true); tally = RunTally(); splits = linkedMapOf(); lastSecured = 0 }
                    if (ticket) run!!.tickets++
                }
                return
            }
            RunTally.sparkling.matchEntire(text)?.let { match ->
                val critter = SafariRoster.named(match.groupValues[2].trim())?.name ?: match.groupValues[2].trim()
                sparklings += SparklingEntry(at, match.groupValues[1], critter, tickets = ticketsSinceSparkling)
                ticketsSinceSparkling = 0
            }
            val current = run ?: return
            if (sending.matches(text)) { close(at); return }
            val before = tally.party.size + tally.caught + tally.lootShares
            tally.line(text, self)
            if (tally.party.size + tally.caught + tally.lootShares != before) lastSecured = at
        }

        private fun split(current: RunRecord, said: String, at: Long) {
            val match = biomeDone.matchEntire(said) ?: return
            val word = match.groupValues[1].lowercase()
            val biome = if (word == "ice") "Icy" else word.replaceFirstChar(Char::uppercaseChar)
            splits.putIfAbsent(biome, at - current.start)
        }

        private fun close(at: Long) {
            val done = run ?: return
            run = null
            if (tally.party.isEmpty()) return
            done.length = maxOf(0L, (if (at > 0) at else lastSecured) - done.start)
            done.party = tally.party.size; done.you = tally.catches.size; done.caught = tally.caught
            done.lootShares = tally.lootShares; done.shards = LinkedHashMap(tally.shards); done.catches = LinkedHashMap(tally.catches)
            done.feathers = tally.feathers; done.sparklings = tally.sparklings.toMutableList(); done.ownSparklings = tally.ownSparklings
            done.macaws = tally.macaws; done.splits = LinkedHashMap(splits)
            runs += done
        }
    }

    private fun files(folder: Path, newerThan: Long): List<LogFile> {
        val found = runCatching { Files.list(folder).use { listing -> listing.toList() } }.getOrDefault(emptyList()).mapNotNull { path ->
            val m = fileName.matchEntire(path.fileName.toString()) ?: return@mapNotNull null
            LogFile(path, LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()),
                m.groupValues[4].toInt(), Files.getLastModifiedTime(path).toMillis())
        }.sortedWith(compareBy({ it.day }, { it.order }))
        return when {
            newerThan == 0L -> found.filter { it.modified == found.maxOfOrNull { f -> f.modified } }
            newerThan > 0L -> found.filter { it.modified > newerThan }
            else -> found
        }
    }

    /** Reads logs off the client thread. [newerThan]: -1 all, 0 just the newest archive, else archives modified after it. */
    fun read(folder: Path, self: String, includeLatest: Boolean, newerThan: Long): CompletableFuture<Result> {
        running = true
        return CompletableFuture.supplyAsync {
            try {
                val logs = files(folder, newerThan).toMutableList()
                val newest = logs.maxOfOrNull { it.modified } ?: 0L
                val latest = folder.resolve("latest.log")
                if (includeLatest && Files.exists(latest)) logs += LogFile(latest,
                    LocalDateTime.ofInstant(Files.getLastModifiedTime(latest).toInstant(), ZoneId.systemDefault()).toLocalDate(), Int.MAX_VALUE, 0)
                bytesTotal = maxOf(1L, logs.sumOf { runCatching { Files.size(it.path) }.getOrDefault(0L) })
                bytesDone = 0; filesTotal = logs.size; filesDone = 0
                val parser = Parser(self)
                for (log in logs) {
                    runCatching {
                        val input = Files.newInputStream(log.path).let { if (log.path.toString().endsWith(".gz")) GZIPInputStream(it) else it }
                        input.bufferedReader().use { parser.read(it, log.day) }
                    }
                    bytesDone += runCatching { Files.size(log.path) }.getOrDefault(0L); filesDone++
                }
                Result(parser.runs, parser.sparklings, logs.size, newest)
            } finally { running = false }
        }
    }

    private fun merge(result: Result): Int {
        val added = RunHistory.importRuns(result.runs)
        if (result.newest > ConfigManager.logsImportedUpTo) { ConfigManager.logsImportedUpTo = result.newest; ConfigManager.save() }
        return added
    }

    /** `/sm history import [folder]`: every log, with a progress bar on the action bar. */
    fun runImport(folder: Path) {
        val client = Minecraft.getInstance()
        if (running) return tell("[SM] An import is already running.")
        if (!Files.isDirectory(folder)) return tell("[SM] No logs folder at $folder")
        val self = client.player?.name?.string.orEmpty()
        tell("[SM] Reading your logs for past runs. Progress is shown above your hotbar.")
        showProgress = true
        read(folder, self, true, -1).whenComplete { result, error -> client.execute {
            showProgress = false
            client.player?.sendOverlayMessage(Component.literal("§aLog import done!"))
            if (error != null || result == null) return@execute tell("[SM] The log import failed; see the game log.")
            val runs = merge(result)
            val catches = SparklingLog.importEntries(result.sparklings)
            val earned = Achievements.afterImport()
            tell("[SM] Read ${result.files} log files: added §e$runs§r run${if (runs == 1) "" else "s"} and §e$catches§r sparkling${if (catches == 1) "" else "s"}." +
                (if (earned > 0) " §6$earned achievement${if (earned == 1) "" else "s"} unlocked!" else "") + " §7Already-saved runs were skipped.")
        } }
    }

    fun tick(client: Minecraft) {
        if (showProgress && running && client.player != null && ++progressTicks % 4 == 0) {
            val fraction = minOf(1.0, bytesDone.toDouble() / bytesTotal)
            val filled = Math.round(fraction * 24).toInt()
            client.player?.sendOverlayMessage(Component.literal("§bImporting logs §a" + "■".repeat(filled) + "§8" + "■".repeat(24 - filled) +
                " §f${Math.round(fraction * 100)}% §7($filesDone/$filesTotal files)"))
        }
        if (autoDone || client.player == null || running) return
        autoDone = true
        if (!ConfigManager.autoImportLogs || !ConfigManager.runHistory || !Files.isDirectory(defaultLogs())) return
        read(defaultLogs(), client.player!!.name.string, false, ConfigManager.logsImportedUpTo).whenComplete { result, _ -> client.execute {
            result ?: return@execute
            val runs = merge(result)
            val catches = SparklingLog.importEntries(result.sparklings)
            val earned = Achievements.afterImport()
            if (runs > 0 || catches > 0) tell("[SM] §7Saved §f$runs§7 run${if (runs == 1) "" else "s"}" +
                (if (catches > 0) " and §f$catches§7 sparkling${if (catches == 1) "" else "s"}" else "") + " from your last session's log." +
                if (earned > 0) " §6$earned achievement${if (earned == 1) "" else "s"} unlocked!" else "")
        } }
    }

    private fun tell(text: String) { Minecraft.getInstance().player?.sendSystemMessage(LocalChat.component(text)) }
}
