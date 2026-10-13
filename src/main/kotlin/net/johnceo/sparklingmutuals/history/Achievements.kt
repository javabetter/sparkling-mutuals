package net.johnceo.sparklingmutuals.history

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.johnceo.sparklingmutuals.LocalChat
import net.johnceo.sparklingmutuals.config.ConfigManager
import net.johnceo.sparklingmutuals.contest.ContestTracker
import net.johnceo.sparklingmutuals.safari.SafariBiome
import net.johnceo.sparklingmutuals.safari.SafariMessages
import net.johnceo.sparklingmutuals.safari.SafariRoster
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.sounds.SoundEvents
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Rarity, lowest first, with its chat colour and ARGB. */
enum class AchievementTier(val code: String, val argb: Int) {
    COMMON("§f", 0xFFFFFFFF.toInt()), UNCOMMON("§a", 0xFF55FF55.toInt()), RARE("§9", 0xFF5555FF.toInt()),
    EPIC("§5", 0xFFAA00AA.toInt()), LEGENDARY("§6", 0xFFFFAA00.toInt()), MYTHIC("§d", 0xFFFF55FF.toInt()),
    DIVINE("§b", 0xFF55FFFF.toInt()), SPECIAL("§c", 0xFFFF5555.toInt())
}

/** What an achievement is measured against; anything but EVENT gets a progress bar. */
enum class Measure { EVENT, RUNS, HOURS, CAUGHT, COINS, OWN_SPARKLINGS, UNIQUES, DRY_SPARKLING, DRY_UNIQUE, RUNS_SINCE_MACAW, CONTESTS }

data class Achievement(val id: String, val name: String, val description: String, val tier: AchievementTier,
    val measure: Measure = Measure.EVENT, val goal: Long = 1, val secret: Boolean = false) {
    val hasProgress get() = measure != Measure.EVENT && goal > 1
}

/**
 * Achievements in the style of SkyBlock Overhaul's: each has a rarity, unlocks once with a chat line and a sound (the
 * challenge fanfare for Mythic and Divine), and some stay secret until earned. Most are worked out from the run and
 * sparkling history, topped up from your own Hypixel profile ([OwnStats]); the rest are chat events (an Emerald Dye). The first time it runs it unlocks whatever
 * the existing history already earns, quietly, in one line.
 */
object Achievements {
    private val tiers = AchievementTier.entries
    val definitions: List<Achievement> = listOf(
        Achievement("first_sparkle", "First Sparkle", "Catch your first sparkling critter.", AchievementTier.COMMON),
        Achievement("party_double", "Shared Shine", "Your party catches 2 sparklings in one run.", AchievementTier.LEGENDARY),
        Achievement("party_triple", "Sparkle Storm", "Your party catches 3 sparklings in one run.", AchievementTier.DIVINE),
        Achievement("own_triple", "Hat Trick", "Catch 3 sparklings yourself in one run.", AchievementTier.SPECIAL),
        Achievement("sparkle_10", "Glimmer", "Catch 10 sparklings.", AchievementTier.UNCOMMON, Measure.OWN_SPARKLINGS, 10),
        Achievement("sparkle_25", "Gleam", "Catch 25 sparklings.", AchievementTier.RARE, Measure.OWN_SPARKLINGS, 25),
        Achievement("sparkle_50", "Radiance", "Catch 50 sparklings.", AchievementTier.EPIC, Measure.OWN_SPARKLINGS, 50),
        Achievement("sparkle_100", "Supernova", "Catch 100 sparklings.", AchievementTier.LEGENDARY, Measure.OWN_SPARKLINGS, 100),
        Achievement("unique_10", "Collector", "Have 10 different sparklings.", AchievementTier.UNCOMMON, Measure.UNIQUES, 10),
        Achievement("unique_20", "Curator", "Have 20 different sparklings.", AchievementTier.RARE, Measure.UNIQUES, 20),
        Achievement("unique_30", "Archivist", "Have 30 different sparklings.", AchievementTier.EPIC, Measure.UNIQUES, 30),
        Achievement("max_sparklings", "Max Sparklings", "Have every sparkling critter.", AchievementTier.DIVINE, Measure.UNIQUES, 37),
        Achievement("legendary_sparkle", "Legend in Lights", "Catch a sparkling Macaw, Wumpa or Doomspiral.", AchievementTier.LEGENDARY),
        Achievement("gemzie_sparkle", "Gem of a Gemzie", "Catch a sparkling Gemzie.", AchievementTier.EPIC),
        Achievement("set_forest", "Forest Glitter", "Have every Forest sparkling.", AchievementTier.EPIC),
        Achievement("set_cavern", "Cavern Glitter", "Have every Cavern sparkling.", AchievementTier.EPIC),
        Achievement("set_icy", "Icy Glitter", "Have every Icy sparkling.", AchievementTier.EPIC),
        Achievement("set_haunted", "Haunted Glitter", "Have every Haunted sparkling.", AchievementTier.EPIC),
        Achievement("dry_runs_10", "Unlucky", "10 runs in a row without catching a sparkling.", AchievementTier.UNCOMMON, Measure.DRY_SPARKLING, 10),
        Achievement("dry_runs_20", "Very Unlucky", "20 runs in a row without catching a sparkling.", AchievementTier.RARE, Measure.DRY_SPARKLING, 20),
        Achievement("dry_runs_30", "Cursed", "30 runs in a row without catching a sparkling.", AchievementTier.EPIC, Measure.DRY_SPARKLING, 30),
        Achievement("dry_runs_40", "Forsaken", "40 runs in a row without catching a sparkling.", AchievementTier.LEGENDARY, Measure.DRY_SPARKLING, 40),
        Achievement("dry_runs_50", "Doomed", "50 runs in a row without catching a sparkling.", AchievementTier.MYTHIC, Measure.DRY_SPARKLING, 50),
        Achievement("no_unique_runs_5", "Same Old Sparkle", "5 runs in a row without a new unique sparkling.", AchievementTier.RARE, Measure.DRY_UNIQUE, 5),
        Achievement("no_unique_runs_10", "Deja Vu", "10 runs in a row without a new unique sparkling.", AchievementTier.EPIC, Measure.DRY_UNIQUE, 10),
        Achievement("no_unique_runs_25", "Groundhog Day", "25 runs in a row without a new unique sparkling.", AchievementTier.LEGENDARY, Measure.DRY_UNIQUE, 25),
        Achievement("no_unique_runs_50", "Broken Record", "50 runs in a row without a new unique sparkling.", AchievementTier.MYTHIC, Measure.DRY_UNIQUE, 50),
        Achievement("no_unique_runs_250", "Eternal Rerun", "250 runs in a row without a new unique sparkling.", AchievementTier.DIVINE, Measure.DRY_UNIQUE, 250),
        Achievement("macaw_3", "Bird Watcher", "Your party secures 3 Macaws in one run.", AchievementTier.RARE),
        Achievement("macaw_4", "Flock Together", "Your party secures 4 Macaws in one run.", AchievementTier.EPIC),
        Achievement("macaw_8", "Macaw Mania", "Your party secures 8 Macaws in one run.", AchievementTier.LEGENDARY),
        Achievement("no_macaw_3", "Where's the Macaw?", "3 runs in a row without a Macaw.", AchievementTier.UNCOMMON, Measure.RUNS_SINCE_MACAW, 3),
        Achievement("no_macaw_5", "Macaw Drought", "5 runs in a row without a Macaw.", AchievementTier.RARE, Measure.RUNS_SINCE_MACAW, 5),
        Achievement("no_macaw_8", "Extinct", "8 runs in a row without a Macaw.", AchievementTier.EPIC, Measure.RUNS_SINCE_MACAW, 8),
        Achievement("emerald_dye", "Emerald Dye", "Find an Emerald Dye.", AchievementTier.DIVINE),
        Achievement("run_10", "Safari Rookie", "Finish 10 Safari runs.", AchievementTier.COMMON, Measure.RUNS, 10),
        Achievement("run_50", "Regular", "Finish 50 Safari runs.", AchievementTier.UNCOMMON, Measure.RUNS, 50),
        Achievement("run_100", "Ranger", "Finish 100 Safari runs.", AchievementTier.RARE, Measure.RUNS, 100),
        Achievement("run_500", "Warden of the Wilds", "Finish 500 Safari runs.", AchievementTier.EPIC, Measure.RUNS, 500),
        Achievement("hours_10", "Time Flies", "Spend 10 hours on Safari runs.", AchievementTier.UNCOMMON, Measure.HOURS, 10),
        Achievement("hours_50", "No Life", "Spend 50 hours on Safari runs.", AchievementTier.EPIC, Measure.HOURS, 50),
        Achievement("party_caught_1000", "Catcher", "Your party catches 1,000 critters with you.", AchievementTier.UNCOMMON, Measure.CAUGHT, 1000),
        Achievement("party_caught_5000", "Trapper", "Your party catches 5,000 critters with you.", AchievementTier.RARE, Measure.CAUGHT, 5000),
        Achievement("party_caught_10000", "Exterminator", "Your party catches 10,000 critters with you.", AchievementTier.EPIC, Measure.CAUGHT, 10000),
        Achievement("party_caught_25000", "Critter Apocalypse", "Your party catches 25,000 critters with you.", AchievementTier.LEGENDARY, Measure.CAUGHT, 25000),
        Achievement("party_caught_50000", "Extinction Event", "Your party catches 50,000 critters with you.", AchievementTier.MYTHIC, Measure.CAUGHT, 50000),
        Achievement("coins_100m", "Shard Shark", "Earn 100M coins of shards and feathers from runs.", AchievementTier.RARE, Measure.COINS, 100_000_000L),
        Achievement("coins_1b", "Shard Tycoon", "Earn 1B coins of shards and feathers from runs.", AchievementTier.LEGENDARY, Measure.COINS, 1_000_000_000L),
        Achievement("full_house", "Full House", "Your party secures all 37 critters in a run.", AchievementTier.UNCOMMON),
        Achievement("speed_3", "Speedrunner", "Secure all 37 critters in a run under 3:00.", AchievementTier.EPIC),
        Achievement("speed_230", "Any%", "Secure all 37 critters in a run under 2:30.", AchievementTier.LEGENDARY),
        Achievement("ten_of_37", "Pulling Your Weight", "Catch 10 different critters yourself in a run.", AchievementTier.RARE),
        Achievement("contests_10", "Contestant", "Complete 10 of Miria's contests.", AchievementTier.UNCOMMON, Measure.CONTESTS, 10),
        Achievement("contests_50", "Honey Hoarder", "Complete 50 of Miria's contests.", AchievementTier.RARE, Measure.CONTESTS, 50),
        Achievement("contests_100", "Miria's Favourite", "Complete 100 of Miria's contests.", AchievementTier.EPIC, Measure.CONTESTS, 100),
        Achievement("night_owl", "Night Owl", "Finish a run between 3 and 5 in the morning.", AchievementTier.UNCOMMON, secret = true),
        Achievement("dedication", "Dedication", "Finish 50 runs in one day.", AchievementTier.RARE, secret = true),
        Achievement("back_to_back", "Back to Back", "Catch a sparkling in two runs in a row.", AchievementTier.LEGENDARY, secret = true),
        Achievement("instant", "Instant Gratification", "Catch a sparkling within 5 tickets of your last one.", AchievementTier.EPIC, secret = true),
    )
    fun all(): List<Achievement> = definitions

    class Data(var initialised: Boolean = false, var unlocked: MutableMap<String, Long> = linkedMapOf(),
        var emblems: MutableMap<String, Long> = linkedMapOf(), var runsSinceOwnSparkling: Int = 0,
        var longestRunsWithoutSparkling: Int = 0, var runsSinceOwnUnique: Int = 0, var longestRunsWithoutUnique: Int = 0,
        var uniqueSinceLastRun: Boolean = false, var runsSinceMacaw: Int = 0, var longestRunsWithoutMacaw: Int = 0,
        var lastRunHadOwnSparkling: Boolean = false, var ticketsSinceOwnSparkling: Int = 0,
        var contestsCompleted: Int = 0, var lastContestWindow: Long = -1)
    private var data = Data()
    private var ticks = 0

    private val emeraldDrop = Regex("^(?:[A-Z!' ]*DROP!|WOW!|RNG METER|NICE!)", RegexOption.IGNORE_CASE)

    fun register() {
        data = HistoryStore.load("achievements", Data::class.java, ::Data)
        // Achievements that were removed (emblems, the 10-run Macaw drought) no longer count.
        data.unlocked.keys.retainAll(definitions.map { it.id }.toSet())
        ClientReceiveMessageEvents.GAME.register { message, overlay ->
            if (!overlay) Minecraft.getInstance().execute { SafariMessages.lines(message.string).forEach(::chat) }
        }
    }

    // ------------------------------------------------------------------ events

    fun tick(client: Minecraft) {
        if (client.player == null || !ConfigManager.achievements) return
        if (!data.initialised) { initialise(client); return }
        // A contest is counted once per window, however many times the sidebar shows it done. The window comes from
        // the tracker with its completion, so a completion from just before a boundary can't count for the next one.
        ContestTracker.completedWindow()?.let { window ->
            if (window != data.lastContestWindow) { data.lastContestWindow = window; data.contestsCompleted++; save(); checkAll(true) }
        }
        if (++ticks % 200 == 0) checkAll(true)
    }

    fun onSparkling(entry: SparklingEntry, own: Boolean) {
        if (own) {
            if (data.ticketsSinceOwnSparkling <= 5 && ownEntries().size > 1) unlock("instant", true)
            data.ticketsSinceOwnSparkling = 0; data.runsSinceOwnSparkling = 0
            if (entry.critter in setOf("Macaw", "Wumpa", "Doomspiral")) unlock("legendary_sparkle", true)
            if (entry.critter == "Gemzie") unlock("gemzie_sparkle", true)
            save()
        }
        checkAll(true)
    }
    fun onUnique(own: Boolean) {
        if (own) { data.runsSinceOwnUnique = 0; data.uniqueSinceLastRun = true; save() }
        checkAll(true)
    }
    fun onTicket() { data.ticketsSinceOwnSparkling++; save() }

    fun onRunEnd(run: RunRecord) {
        data.runsSinceMacaw = if (run.macaws > 0) 0 else data.runsSinceMacaw + 1
        data.longestRunsWithoutMacaw = maxOf(data.longestRunsWithoutMacaw, data.runsSinceMacaw)
        val own = run.ownSparklings > 0
        if (!own) data.runsSinceOwnSparkling++
        data.longestRunsWithoutSparkling = maxOf(data.longestRunsWithoutSparkling, data.runsSinceOwnSparkling)
        if (!data.uniqueSinceLastRun) data.runsSinceOwnUnique++
        data.longestRunsWithoutUnique = maxOf(data.longestRunsWithoutUnique, data.runsSinceOwnUnique)
        data.uniqueSinceLastRun = false
        if (own && data.lastRunHadOwnSparkling) unlock("back_to_back", true)
        data.lastRunHadOwnSparkling = own
        save()
        checkRun(run, true)
        checkAll(true)
    }

    private fun chat(text: String) {
        if (!ConfigManager.achievements) return
        if ("Emerald Dye" in text && ownDrop(text)) unlock("emerald_dye", true)
    }
    /** Your drop, not a broadcast of someone else's ("WOW! Name found Emerald Dye"). */
    fun ownDrop(text: String, self: String = Minecraft.getInstance()?.player?.name?.string.orEmpty()): Boolean {
        if (!emeraldDrop.containsMatchIn(text) && "You found" !in text) return false
        val found = text.indexOf(" found ")
        if (found < 0) return true
        val before = text.substring(0, found)
        return before.endsWith("You") || self.isNotEmpty() && before.endsWith(self)
    }

    // ------------------------------------------------------------------ checking

    private fun checkAll(announce: Boolean) {
        if (!data.initialised) return
        for (achievement in definitions)
            if (achievement.measure != Measure.EVENT && !isUnlocked(achievement) && progress(achievement) >= achievement.goal) unlock(achievement.id, announce)
        val dex = ownDex()
        if (dex.isNotEmpty()) unlock("first_sparkle", announce)
        for (biome in SafariBiome.entries) if (dex.containsAll(biome.critters.map { it.name }))
            unlock("set_${biome.name.lowercase()}", announce)
    }

    private fun checkRun(run: RunRecord, announce: Boolean) {
        if (run.sparklings.size >= 2) unlock("party_double", announce)
        if (run.sparklings.size >= 3) unlock("party_triple", announce)
        if (run.ownSparklings >= 3) unlock("own_triple", announce)
        if (run.macaws >= 3) unlock("macaw_3", announce)
        if (run.macaws >= 4) unlock("macaw_4", announce)
        if (run.macaws >= 8) unlock("macaw_8", announce)
        val all = SafariRoster.all.size
        if (run.party >= all) unlock("full_house", announce)
        if (run.party >= all && run.length < 180_000) unlock("speed_3", announce)
        if (run.party >= all && run.length < 150_000) unlock("speed_230", announce)
        if (run.you >= 10) unlock("ten_of_37", announce)
        val zone = ZoneId.systemDefault()
        if (Instant.ofEpochMilli(run.start + run.length).atZone(zone).hour in 3..4) unlock("night_owl", announce)
        val day = Instant.ofEpochMilli(run.start).atZone(zone).toLocalDate()
        if (RunHistory.runs.count { Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate() == day } >= 50) unlock("dedication", announce)
    }

    fun progress(achievement: Achievement): Long = when (achievement.measure) {
        Measure.EVENT -> if (isUnlocked(achievement)) 1 else 0
        // Hypixel's bestiary also counts runs and catches from before the mod was installed.
        Measure.RUNS -> maxOf(RunHistory.runs.size.toLong(), OwnStats.runs)
        Measure.HOURS -> PlaytimeReader.safariMillis() / 3_600_000L
        Measure.CAUGHT -> maxOf(RunHistory.runs.sumOf { (it.caught + it.lootShares).toLong() }, OwnStats.caught)
        Measure.COINS -> RunHistory.runs.sumOf { maxOf(0L, it.coins) }
        Measure.OWN_SPARKLINGS -> ownEntries().size.toLong()
        Measure.UNIQUES -> ownDex().size.toLong()
        Measure.DRY_SPARKLING -> maxOf(data.longestRunsWithoutSparkling, data.runsSinceOwnSparkling).toLong()
        Measure.DRY_UNIQUE -> maxOf(data.longestRunsWithoutUnique, data.runsSinceOwnUnique).toLong()
        Measure.RUNS_SINCE_MACAW -> maxOf(data.longestRunsWithoutMacaw, data.runsSinceMacaw).toLong()
        Measure.CONTESTS -> data.contestsCompleted.toLong()
    }

    fun isUnlocked(achievement: Achievement) = achievement.id in data.unlocked
    fun unlockedAt(achievement: Achievement) = data.unlocked[achievement.id] ?: 0L

    /** With achievements off, progress still counts but nothing unlocks or is announced; it catches up when turned on. */
    private fun unlock(id: String, announce: Boolean) {
        if (!ConfigManager.achievements || id in data.unlocked) return
        val achievement = definitions.firstOrNull { it.id == id } ?: return
        data.unlocked[id] = System.currentTimeMillis()
        save()
        if (announce) announce(achievement)
    }

    /** "ACHIEVEMENT UNLOCKED! Macaw Mania" in the tier's colour, the description on hover. */
    private fun announce(achievement: Achievement) {
        if (!ConfigManager.achievements) return
        val client = Minecraft.getInstance()
        val player = client.player ?: return
        val tier = achievement.tier
        val hover = Component.literal("${tier.code}§l${achievement.name}\n§7${achievement.description}\n\n${tier.code}§l${tier.name} ACHIEVEMENT")
        player.sendSystemMessage(LocalChat.component("[SM] §6§lACHIEVEMENT UNLOCKED! ${tier.code}§l${achievement.name}").copy()
            .withStyle { it.withHoverEvent(HoverEvent.ShowText(hover)) })
        player.sendSystemMessage(LocalChat.component("[SM]   §7${achievement.description} §8(${tier.code}${tier.name}§8)"))
        if (ConfigManager.achievementSound) {
            // Mythic and Divine get the challenge-advancement fanfare; the rest a lighter chime.
            if (tier.ordinal >= AchievementTier.MYTHIC.ordinal) player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f)
            else player.playSound(SoundEvents.PLAYER_LEVELUP, .6f, 1.6f)
        }
        if (tier.ordinal >= AchievementTier.LEGENDARY.ordinal) {
            client.gui.setTimes(5, 50, 15)
            client.gui.setSubtitle(Component.literal("${tier.code}${achievement.name}"))
            client.gui.setTitle(Component.literal("§6§lACHIEVEMENT"))
        }
        if (ConfigManager.achievementsToParty) player.connection.sendCommand("pc Achievement unlocked: ${achievement.name}!")
    }

    /** First run: unlock what the history already earns, quietly, and say how many in one line. */
    private fun initialise(client: Minecraft) {
        data.initialised = true
        val before = data.unlocked.size
        afterImport()
        val earned = data.unlocked.size - before
        if (earned > 0) client.player?.sendSystemMessage(LocalChat.component(
            "[SM] Achievements are here! Your history already earns §e$earned§r of them. See /sm achievements."))
    }

    /**
     * After history is imported: streaks are worked out again from the whole run history (they only grow), and every
     * run and catch is checked, quietly. Returns how many unlocked.
     */
    fun afterImport(): Int {
        if (!data.initialised) return 0
        val before = data.unlocked.size
        var noMacaw = 0; var noSparkle = 0; var lastOwn = false
        for (run in RunHistory.runs.sortedBy { it.start }) {
            noMacaw = if (run.macaws > 0) 0 else noMacaw + 1
            data.longestRunsWithoutMacaw = maxOf(data.longestRunsWithoutMacaw, noMacaw)
            noSparkle = if (run.ownSparklings > 0) 0 else noSparkle + 1
            data.longestRunsWithoutSparkling = maxOf(data.longestRunsWithoutSparkling, noSparkle)
            if (run.ownSparklings > 0 && lastOwn) unlock("back_to_back", false)
            lastOwn = run.ownSparklings > 0
            checkRun(run, false)
        }
        if (RunHistory.runs.isNotEmpty()) { data.runsSinceMacaw = noMacaw; data.runsSinceOwnSparkling = noSparkle; data.lastRunHadOwnSparkling = lastOwn }
        for (entry in ownEntries()) {
            if (entry.critter in setOf("Macaw", "Wumpa", "Doomspiral")) unlock("legendary_sparkle", false)
            if (entry.critter == "Gemzie") unlock("gemzie_sparkle", false)
        }
        checkAll(false)
        save()
        return data.unlocked.size - before
    }

    // ------------------------------------------------------------------ reading

    fun ownEntries(): List<SparklingEntry> {
        val self = Minecraft.getInstance()?.player?.name?.string ?: return emptyList()
        return SparklingLog.entries.filter { it.player.equals(self, true) }
    }
    /** Your sparkling dex: your history, plus your Sparkling Critterdex from the API. */
    fun ownDex(): Set<String> = ownEntries().mapNotNull { SafariRoster.named(it.critter)?.name }.toSet() + OwnStats.dex

    /** Your profile was read from the API: whatever its runs, catches and dex earn is unlocked quietly. Returns how many. */
    fun afterApiStats(): Int {
        if (!data.initialised || !ConfigManager.achievements) return 0
        val before = data.unlocked.size
        checkAll(false)
        save()
        return data.unlocked.size - before
    }
    val longestDrySparkling get() = maxOf(data.longestRunsWithoutSparkling, data.runsSinceOwnSparkling)
    val longestMacawDrought get() = maxOf(data.longestRunsWithoutMacaw, data.runsSinceMacaw)
    val runsSinceMacaw get() = data.runsSinceMacaw
    val contestsCompleted get() = data.contestsCompleted
    fun save() = HistoryStore.save("achievements", data)
}
