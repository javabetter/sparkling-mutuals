package net.johnceo.sparklingmutuals.config

import net.johnceo.sparklingmutuals.LocalChat

import io.github.notenoughupdates.moulconfig.Config
import io.github.notenoughupdates.moulconfig.annotations.*
import io.github.notenoughupdates.moulconfig.common.text.StructuredText
import io.github.notenoughupdates.moulconfig.processor.ProcessedCategory
import net.johnceo.sparklingmutuals.alerts.AlertManager
import net.johnceo.sparklingmutuals.commands.*
import net.johnceo.sparklingmutuals.contest.ContestGui
import net.johnceo.sparklingmutuals.safari.CatchCountScreen
import net.johnceo.sparklingmutuals.safari.SafariFullClear
import net.johnceo.sparklingmutuals.safari.SafariMode
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.client.gui.screens.ChatScreen

/** MoulConfig view model; the existing properties files remain the saved configuration. */
class SafariSettings : Config() {
    @JvmField @Category(name = "General", desc = "") val general = General()
    @JvmField @Category(name = "Modes", desc = "") val modes = Modes()
    @JvmField @Category(name = "Party commands", desc = "") val party = Party()
    @JvmField @Category(name = "Miria contest", desc = "") val miria = Miria()
    @JvmField @Category(name = "Honeyhives", desc = "") val honey = Honey()
    @JvmField @Category(name = "History", desc = "") val history = History()
    @JvmField @Category(name = "Warp reminders", desc = "") val warp = Warp()
    @JvmField @Category(name = "Safari helpers", desc = "") val safari = Safari()
    @JvmField @Category(name = "Safari progress", desc = "") val tracking = Tracking()
    @JvmField @Category(name = "API key", desc = "") val api = Api()
    @JvmField @Category(name = "ESP", desc = "") val esp = SafariEspSettings()

    @JvmField @Category(name = "Customization", desc = "") val customization = CustomizationSettings()

    class General {
        @JvmField @ConfigOption(name = "Command help", desc = "Close settings and show commands in chat.")
        @ConfigEditorButton(runnableId = 3, buttonText = "Show help") var help = false
        @JvmField @ConfigOption(name = "Move and resize HUDs", desc = "Drag HUDs to move them; scroll to resize.")
        @ConfigEditorButton(runnableId = 1, buttonText = "Edit all HUDs") var edit = false
    }
    class Modes {
        @JvmField @ConfigOption(name = "Unique mode", desc = "") @Accordion val unique = Unique()
        @JvmField @ConfigOption(name = "Full clear mode", desc = "") @Accordion val fullClear = FullClear()
        @JvmField @ConfigOption(name = "Sparkling mode", desc = "") @Accordion val sparkling = Sparkling()
        private var selected = SafariFullClear.mode
        fun selection(): SafariMode {
            val toggles = listOf(SafariMode.UNIQUE to unique.enabled, SafariMode.FULL_CLEAR to fullClear.enabled,
                SafariMode.SPARKLING to sparkling.enabled)
            return toggles.firstOrNull { (mode, enabled) -> enabled && mode != selected }?.first
                ?: selected.takeIf { mode -> toggles.any { it.first == mode && it.second } } ?: SafariMode.UNIQUE
        }
        fun refresh() {
            selected = SafariFullClear.mode
            unique.enabled = selected == SafariMode.UNIQUE
            fullClear.enabled = selected == SafariMode.FULL_CLEAR
            sparkling.enabled = selected == SafariMode.SPARKLING
        }
    }
    class FullClear {
        @JvmField @ConfigOption(name = "Full clear mode", desc = "Highlight all critters and track full-clear PBs.")
        @ConfigEditorBoolean var enabled = SafariFullClear.mode == SafariMode.FULL_CLEAR
    }
    class Sparkling {
        @JvmField @ConfigOption(name = "Sparkling Mode", desc = "Highlight missing sparklings for you or your party. Requires an API key.")
        @ConfigEditorBoolean var enabled = ConfigManager.sparklingMode
        @JvmField @ConfigOption(name = "Critter ESP for profitable shards", desc = "Keep profitable critters highlighted throughout the run.")
        @ConfigEditorBoolean var profitable = ConfigManager.sparklingProfitableShardEsp
    }
    class Unique {
        @JvmField @ConfigOption(name = "Unique mode", desc = "Highlight missing uniques and track unique-run PBs.")
        @ConfigEditorBoolean var enabled = SafariFullClear.mode == SafariMode.UNIQUE
        // Retain the saved preference for compatibility; Unique entities now always hide after a catch.
        var profitable = ConfigManager.profitableShardEsp
    }
    class Party {
        @JvmField @ConfigOption(name = "Respond to party commands", desc = "Reply to party commands; each mod user shares their PB.")
        @ConfigEditorBoolean var enabled = ConfigManager.partyCommandsEnabled
        @JvmField @ConfigOption(name = "!dt downtime", desc = "After a run, hold the Safari Manager for whoever said !dt until they say r. Sneak-click skips.")
        @ConfigEditorBoolean var downtime = ConfigManager.downtimeCommand

    }
    class Miria {
        @JvmField @ConfigOption(name = "Contest HUD and tracking", desc = "Track Miria contests.")
        @ConfigEditorBoolean var enabled = ContestConfig.trackContest
        @JvmField @ConfigOption(name = "Warning", desc = "Contest warnings and sound.") @Accordion val warning = Warning()
        var five
            get() = warning.five
            set(value) { warning.five = value }
        var three
            get() = warning.three
            set(value) { warning.three = value }
        var one
            get() = warning.one
            set(value) { warning.one = value }
        var titles
            get() = warning.titles
            set(value) { warning.titles = value }
        var volume
            get() = warning.volume
            set(value) { warning.volume = value }
        var sound
            get() = warning.sound
            set(value) { warning.sound = value }

    }
    class Warning {
        @JvmField @ConfigOption(name = "5 minute warning", desc = "Warn at 5 minutes if below Uncommon.")
        @ConfigEditorBoolean var five = 5 in ContestConfig.warningMinutes()
        @JvmField @ConfigOption(name = "3 minute warning", desc = "Warn at 3 minutes if below Uncommon.")
        @ConfigEditorBoolean var three = 3 in ContestConfig.warningMinutes()
        @JvmField @ConfigOption(name = "1 minute warning", desc = "Warn at 1 minute if below Uncommon.")
        @ConfigEditorBoolean var one = 1 in ContestConfig.warningMinutes()
        @JvmField @ConfigOption(name = "No contest warnings", desc = "Disable contest warnings.")
        @ConfigEditorButton(runnableId = 2, buttonText = "None") var none = false
        @JvmField @ConfigOption(name = "Warning titles", desc = "Show contest warnings as titles.")
        @ConfigEditorBoolean var titles = ContestConfig.contestWarnTitle
        @JvmField @ConfigOption(name = "Sound volume", desc = "Contest warning volume.")
        @ConfigEditorSlider(minValue = 0f, maxValue = 100f, minStep = 1f) var volume = ContestConfig.contestSoundVolume.coerceIn(0, 100)
        @JvmField @ConfigOption(name = "Warning sound", desc = "Sound identifier for contest warnings.")
        @ConfigEditorText var sound = ContestConfig.contestSound
        @JvmField @ConfigOption(name = "Save sound", desc = "Apply the warning sound.")
        @ConfigEditorButton(runnableId = 7, buttonText = "Save sound") var saveSound = false
    }
    class Honey {
        @JvmField @ConfigOption(name = "Honeyhive beacons", desc = "Beacons over full Torrhus Canyon honeyhives.")
        @ConfigEditorBoolean var enabled = ConfigManager.honeyHives
        @JvmField @ConfigOption(name = "Only while contest incomplete", desc = "Hide the beacons once Miria's contest is done.")
        @ConfigEditorBoolean var onlyIncomplete = ConfigManager.honeyHivesOnlyIncomplete
        @JvmField @ConfigOption(name = "Refill countdown", desc = "Over a hive you looted, show how long until it refills. Remembered across restarts.")
        @ConfigEditorBoolean var refillLabels = ConfigManager.honeyHiveRefillLabels
    }
    class History {
        @JvmField @ConfigOption(name = "Save runs", desc = "Record every Safari run for /sm history: captures, shards, sparklings, splits.")
        @ConfigEditorBoolean var runs = ConfigManager.runHistory
        @JvmField @ConfigOption(name = "Import logs on launch", desc = "Save runs from your last session's log when the game starts. Minecraft keeps only 7 logs a day.")
        @ConfigEditorBoolean var autoImport = ConfigManager.autoImportLogs
        @JvmField @ConfigOption(name = "Coins at insta-sell", desc = "Value shards and feathers at the bazaar's instant-sell price instead of the sell-offer price.")
        @ConfigEditorBoolean var instaSell = ConfigManager.runCoinsInstaSell
        @JvmField @ConfigOption(name = "Achievements", desc = "Unlock achievements as you play (/sm achievements).")
        @ConfigEditorBoolean var achievements = ConfigManager.achievements
        @JvmField @ConfigOption(name = "Achievement sound", desc = "Play a sound when an achievement unlocks.")
        @ConfigEditorBoolean var sound = ConfigManager.achievementSound
        @JvmField @ConfigOption(name = "Achievements to party", desc = "Also say \"Achievement unlocked: X!\" in party chat.")
        @ConfigEditorBoolean var toParty = ConfigManager.achievementsToParty
        @JvmField @ConfigOption(name = "Open history", desc = "Runs, bests, sparklings, achievements and stats.")
        @ConfigEditorButton(runnableId = 10, buttonText = "Open") var open = false
    }
    class Warp {
        @JvmField @ConfigOption(name = "Warp reminder", desc = "Remind you to warp party leechers in before entry closes.")
        @ConfigEditorBoolean var enabled = ConfigManager.warpAlertsEnabled
        @JvmField @ConfigOption(name = "Delay in seconds", desc = "Reminder delay, from 1 to 86400 seconds.")
        @ConfigEditorText var delay = ConfigManager.warpDelaySeconds.toString()
        @JvmField @ConfigOption(name = "Save delay", desc = "Apply the reminder delay.")
        @ConfigEditorButton(runnableId = 6, buttonText = "Save delay") var saveDelay = false
        @JvmField @ConfigOption(name = "Default delay", desc = "Restore the 25-second delay.")
        @ConfigEditorButton(runnableId = 5, buttonText = "Reset to 25s") var reset = false
    }
    class Safari {
        @JvmField @ConfigOption(name = "Highlight teammates", desc = "Outline loaded party members anywhere in SkyBlock.")
        @ConfigEditorBoolean var highlightTeammates = ConfigManager.highlightTeammates
        @JvmField @ConfigOption(name = "Show teammate names", desc = "Show party member names through walls anywhere in SkyBlock.")
        @ConfigEditorBoolean var showTeammateNames = ConfigManager.showTeammateNames
        @JvmField @ConfigOption(name = "Alert", desc = "") @Accordion val alerts = Alerts()
        @JvmField @ConfigOption(name = "Hide useless chats in safari", desc = "Hide throws, captures, escapes, loot shares and mound chatter; tracking continues.")
        @ConfigEditorBoolean var hideCaptureChat = ConfigManager.hideCaptureChat
        @JvmField @ConfigOption(name = "Hide capsules on ground", desc = "Hide ordinary dropped capsules in Safari.")
        @ConfigEditorBoolean var hideGroundCapsules = ConfigManager.hideGroundCapsules
        @JvmField @ConfigOption(name = "Hide flying capsules", desc = "Hide flying capsules near the camera in Safari.")
        @ConfigEditorBoolean var hideFlyingCapsules = ConfigManager.hideFlyingCapsules
        @JvmField @ConfigOption(name = "Flying capsule distance", desc = "Hide flying capsules within this distance, in blocks.")
        @ConfigEditorSlider(minValue = .5f, maxValue = 6f, minStep = .5f)
        var capsuleHideDistance = ConfigManager.capsuleHideDistance
        @JvmField @ConfigOption(name = "Full candle hitbox", desc = "Easier candle clicks in Haunted while holding Soothing Incense.")
        @ConfigEditorBoolean var candleHitbox = ConfigManager.candleHitbox
        @JvmField @ConfigOption(name = "Hold Safari Manager on kick", desc = "After a kick is called in party chat, block the Safari Manager and tickets on the Safari island for 30s. Sneak to go anyway.")
        @ConfigEditorBoolean var holdManagerOnKick = ConfigManager.holdManagerOnKick
        @JvmField @ConfigOption(name = "Auto Clicker", desc = "Hold Mouse 0 on Rockmite mounds for 12 CPS.")
        @ConfigEditorBoolean var autoClicker = ConfigManager.autoClicker
        @JvmField @ConfigOption(name = "Hideyho quest clicks", desc = "Click with chat open to accept Hideyho’s offer.")
        @ConfigEditorBoolean var hideyho = ConfigManager.hideyhoQuestClicks
        @JvmField @ConfigOption(name = "Hideyho inside/outside", desc = "Title and chat line saying whether Hideyho hid inside or outside the mansion.")
        @ConfigEditorBoolean var hideyhoInsideOutside = ConfigManager.hideyhoInsideOutside
        @JvmField @ConfigOption(name = "Hide Haunted paintings", desc = "Hide paintings in the Haunted biome.")
        @ConfigEditorBoolean var paintings = ConfigManager.hideHauntedPaintings
        @JvmField @ConfigOption(name = "Nearby shiny detection", desc = "Highlight sparkling critters within 80 blocks in your biome.")
        @ConfigEditorBoolean var shiny = ConfigManager.shinyDetection
        @JvmField @ConfigOption(name = "Remove darkness", desc = "Remove the darkness effect in Safari.")
        @ConfigEditorBoolean var darkness = ConfigManager.removeDarkness
        @JvmField @ConfigOption(name = "Sparkling alert", desc = "Show an alert for each detected sparkling critter.")
        @ConfigEditorBoolean var alert = ConfigManager.sparklingAlert
        @JvmField @ConfigOption(name = "Announce sparklings to party", desc = "Announce sparkling critters with biome and coordinates.")
        @ConfigEditorBoolean var announce = ConfigManager.sparklingPartyAnnouncer
    }
    class Alerts {
        @JvmField @ConfigOption(name = "Sparkling sound", desc = "Minecraft sound identifier for sparkling alerts.")
        @ConfigEditorText var sound = ConfigManager.sparklingSound
        @JvmField @ConfigOption(name = "Sparkling sound volume", desc = "Set to zero to mute.")
        @ConfigEditorSlider(minValue = 0f, maxValue = 100f, minStep = 1f) var volume = ConfigManager.sparklingSoundVolume
        @JvmField @ConfigOption(name = "Save sparkling sound", desc = "Apply and preview the sound.")
        @ConfigEditorButton(runnableId = 9, buttonText = "Save sound") var saveSound = false
        @JvmField @ConfigOption(name = "All Gems Alert", desc = "Cavern: one Purple, Lime and Orange Gem in inventory.")
        @ConfigEditorBoolean var gems = ConfigManager.allGemsAlert
        @JvmField @ConfigOption(name = "All Bird Food Alert", desc = "Alert after collecting three of each Forest bird food.")
        @ConfigEditorBoolean var birdFood = ConfigManager.allBirdFoodAlert
        @JvmField @ConfigOption(name = "All Incense Alert", desc = "Haunted: four Soothing Incense in inventory.")
        @ConfigEditorBoolean var incense = ConfigManager.allIncenseAlert
        @JvmField @ConfigOption(name = "Run completion alert", desc = "Show and sound an alert once when all biomes complete in the active mode.")
        @ConfigEditorBoolean var runComplete = ConfigManager.runCompleteAlert
        @JvmField @ConfigOption(name = "Run completion title", desc = "Text shown by the completion alert.")
        @ConfigEditorText var runTitle = ConfigManager.runCompleteTitle
        @JvmField @ConfigOption(name = "Run completion sound", desc = "Minecraft sound identifier; saved when settings close.")
        @ConfigEditorText var runSound = ConfigManager.runCompleteSound
        @JvmField @ConfigOption(name = "Run completion volume", desc = "Set to zero to mute.")
        @ConfigEditorSlider(minValue = 0f, maxValue = 100f, minStep = 1f) var runVolume = ConfigManager.runCompleteVolume
    }
    class Tracking {
        @JvmField @ConfigOption(name = "Progress HUD", desc = "Show Safari completion and elapsed time.")
        @ConfigEditorBoolean var progress = ConfigManager.progressHud
        @JvmField @ConfigOption(name = "Show where", desc = "Where the Progress HUD appears.")
        @ConfigEditorDropdown(values = ["Only in Safari", "Safari and entrance", "Everywhere"])
        var where = ConfigManager.showWhere
        @JvmField @ConfigOption(name = "Missing panel", desc = "List missing critters in your current biome.")
        @ConfigEditorBoolean var missing = ConfigManager.missingPanel
        @JvmField @ConfigOption(name = "Capture count screen", desc = "Review the current or last run's captures.")
        @ConfigEditorButton(runnableId = 8, buttonText = "View captures") var view = false

    }
    class Api {
        @JvmField @ConfigOption(name = "Use Shiny Hunter cloud API", desc = "With no key of your own, look players up through Shiny Hunter's shared API. Your own key always takes priority.")
        @ConfigEditorBoolean var cloud = ConfigManager.useCloudApi
        @JvmField @ConfigOption(name = "Hypixel API key", desc = "Optional with the cloud API. Edit the key used for profile lookups.")
        @ConfigEditorButton(runnableId = 4, buttonText = "Edit API key") var edit = false
    }

    override fun getTitle(): StructuredText = StructuredText.of("Sparkling Mutuals · Safari").withColour(SafariTheme.ACCENT)
    override fun formatCategoryName(category: ProcessedCategory, selected: Boolean): StructuredText =
        category.displayName.copyShallow().withColour(if (selected) SafariTheme.ACCENT else 0xC5B99E)

    private fun feedback(text: String) { Minecraft.getInstance().player?.sendSystemMessage(LocalChat.component("[SM] $text")) }
    override fun executeRunnable(id: Int) {
        val client = Minecraft.getInstance()
        when (id) {
            1 -> { apply(); client.execute { client.setScreen(ContestGui(client.screen)) } }
            2 -> { miria.five = false; miria.three = false; miria.one = false; apply() }
            3 -> client.execute {
                client.setScreen(ChatScreen("", false))
                client.player?.sendSystemMessage(LocalChat.component(CommandHelp.localLines().joinToString("\n")))
            }
            4 -> client.execute { client.setScreen(ApiKeyScreen(client.screen)) }
            5 -> { warp.delay = "25"; saveDelay() }
            6 -> saveDelay()
            7 -> saveSound()
            8 -> { apply(); client.execute { client.setScreen(CatchCountScreen(client.screen)) } }
            9 -> { apply(); if (saveSparklingSound()) net.johnceo.sparklingmutuals.safari.SparklingEncounters.playSound(client) }
            10 -> { apply(); client.execute { client.setScreen(net.johnceo.sparklingmutuals.history.HistoryScreen(client.screen)) } }
        }
    }
    override fun isValidRunnable(id: Int) = id in 1..10
    private fun saveDelay() {
        val seconds = warp.delay.trim().toIntOrNull()
        if (seconds == null || seconds !in 1..86400) feedback("Enter a delay from 1 to 86400 seconds.")
        else if (seconds != ConfigManager.warpDelaySeconds) AlertManager.setDelay(seconds)
    }
    private fun saveSound() {
        val sound = miria.sound.trim()
        if (Identifier.tryParse(sound) == null) feedback("Enter a valid Minecraft sound identifier.")
        else if (ContestConfig.contestSound != sound) { ContestConfig.contestSound = sound; ContestConfig.save() }
    }
    private fun saveSparklingSound(): Boolean {
        val sound = safari.alerts.sound.trim()
        if (Identifier.tryParse(sound) == null) { feedback("Enter a valid Minecraft sound identifier."); return false }
        if (ConfigManager.sparklingSound != sound) { ConfigManager.sparklingSound = sound; ConfigManager.save() }
        return true
    }
    fun saveTextFields() {
        saveDelay(); saveSound(); saveSparklingSound()
        val sound = Identifier.tryParse(safari.alerts.runSound.trim())
        if (sound == null) feedback("Enter a valid Minecraft sound identifier for run completion.")
        else ConfigManager.runCompleteSound = sound.toString()
        ConfigManager.runCompleteTitle = safari.alerts.runTitle.take(120).ifBlank { "Run Complete" }
        ConfigManager.save()
    }
    fun apply(notifyMode: Boolean = false) {
        if (warp.enabled != ConfigManager.warpAlertsEnabled) AlertManager.toggleAlert()
        val changes = listOf(ConfigManager::highlightTeammates to safari.highlightTeammates, ConfigManager::showTeammateNames to safari.showTeammateNames, ConfigManager::profitableShardEsp to modes.unique.profitable, ConfigManager::sparklingProfitableShardEsp to modes.sparkling.profitable, ConfigManager::partyCommandsEnabled to party.enabled, ConfigManager::downtimeCommand to party.downtime, ConfigManager::useCloudApi to api.cloud, ConfigManager::hideHauntedPaintings to safari.paintings,
            ConfigManager::shinyDetection to safari.shiny, ConfigManager::hideyhoQuestClicks to safari.hideyho, ConfigManager::hideyhoInsideOutside to safari.hideyhoInsideOutside,
            ConfigManager::progressHud to tracking.progress,
            ConfigManager::missingPanel to tracking.missing,
            ConfigManager::removeDarkness to safari.darkness,
            ConfigManager::sparklingAlert to safari.alert, ConfigManager::sparklingPartyAnnouncer to safari.announce,
            ConfigManager::autoClicker to safari.autoClicker, ConfigManager::candleHitbox to safari.candleHitbox, ConfigManager::holdManagerOnKick to safari.holdManagerOnKick, ConfigManager::honeyHives to honey.enabled, ConfigManager::honeyHivesOnlyIncomplete to honey.onlyIncomplete, ConfigManager::honeyHiveRefillLabels to honey.refillLabels, ConfigManager::hideCaptureChat to safari.hideCaptureChat, ConfigManager::runHistory to history.runs, ConfigManager::autoImportLogs to history.autoImport, ConfigManager::achievements to history.achievements, ConfigManager::achievementSound to history.sound, ConfigManager::achievementsToParty to history.toParty, ConfigManager::runCoinsInstaSell to history.instaSell,
            ConfigManager::allGemsAlert to safari.alerts.gems, ConfigManager::allBirdFoodAlert to safari.alerts.birdFood,
            ConfigManager::allIncenseAlert to safari.alerts.incense, ConfigManager::runCompleteAlert to safari.alerts.runComplete,
            ConfigManager::hideGroundCapsules to safari.hideGroundCapsules, ConfigManager::hideFlyingCapsules to safari.hideFlyingCapsules)
        val espChanged = esp.apply()
        val customizationChanged = customization.apply()
        val distance = ConfigManager.validCapsuleDistance(safari.capsuleHideDistance)
        val generalChanged = espChanged || customizationChanged || changes.any { (property, value) -> property.get() != value } ||
            ConfigManager.showWhere != tracking.where || ConfigManager.capsuleHideDistance != distance ||
            ConfigManager.runCompleteVolume != safari.alerts.runVolume.coerceIn(0, 100) || ConfigManager.sparklingSoundVolume != safari.alerts.volume.coerceIn(0, 100)
        changes.forEach { (property, value) -> property.set(value) }
        ConfigManager.showWhere = tracking.where.coerceIn(0, 2)
        ConfigManager.capsuleHideDistance = distance
        ConfigManager.runCompleteVolume = safari.alerts.runVolume.coerceIn(0, 100)
        ConfigManager.sparklingSoundVolume = safari.alerts.volume.coerceIn(0, 100)
        if (generalChanged) ConfigManager.save()
        val warnings = listOfNotNull(5.takeIf { miria.five }, 3.takeIf { miria.three }, 1.takeIf { miria.one })
        val contestChanged = ContestConfig.trackContest != miria.enabled || ContestConfig.contestWarnTitle != miria.titles ||
            ContestConfig.contestSoundVolume != miria.volume || ContestConfig.warningMinutes() != warnings
        ContestConfig.trackContest = miria.enabled
        ContestConfig.contestWarnTitle = miria.titles
        ContestConfig.contestSoundVolume = miria.volume.coerceIn(0, 100)
        ContestConfig.contestWarnMinutes = warnings.joinToString(", ")
        ContestConfig.contestWarnEnabled = warnings.isNotEmpty()
        if (contestChanged) ContestConfig.save()
        val selection = modes.selection()
        if (selection != SafariFullClear.mode) {
            SafariFullClear.select(selection)
            if (notifyMode) SafariFullClear.modeLines().forEach {
                Minecraft.getInstance().player?.sendSystemMessage(LocalChat.component(it))
            }
        }
        modes.refresh()
    }
}
