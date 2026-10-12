package net.johnceo.sparklingmutuals.config

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import net.johnceo.sparklingmutuals.hud.SafariHud
import net.johnceo.sparklingmutuals.safari.SafariPersonalBests
import net.johnceo.sparklingmutuals.safari.SafariFullClear
import net.minecraft.resources.Identifier

object ConfigManager {
    private lateinit var configPath: Path
    @Volatile var apiKey = ""
    var highlightTeammates = true
    var showTeammateNames = true
    var partyCommandsEnabled = true
    var useCloudApi = true
    var fullClearMode = false
    var sparklingMode = false
    var sparklingProfitableShardEsp = true
    var profitableShardEsp = true
    var timesaveOnly = true
    var warpAlertsEnabled = false
    var warpDelaySeconds = 25
    var hideyhoQuestClicks = true
    var hideHauntedPaintings = true
    var shinyDetection = true
    var progressHud = true
    var countUniqueOnly = false
    var missingPanel = true
    var highlightBeeNests = true
    var highlightSnooperWalls = true
    var showBeeNests = true
    var showMoundCount = true
    var showSnooperWalls = true
    var showMoundStats = false
    var removeDarkness = true
    var sparklingAlert = true
    var sparklingSound = "minecraft:block.amethyst_block.chime"
    var sparklingSoundVolume = 100
    var allGemsAlert = true
    var allBirdFoodAlert = true
    var allIncenseAlert = true
    var runCompleteAlert = true
    var runCompleteTitle = "Run Complete"
    var runCompleteSound = "minecraft:block.bell.use"
    var runCompleteVolume = 100
    var sparklingPartyAnnouncer = true
    var catchCountPanel = false
    var autoClicker = true
    var candleHitbox = true
    var holdManagerOnKick = true
    var downtimeCommand = true
    var honeyHives = true
    var honeyHivesOnlyIncomplete = true
    var honeyHiveRefillLabels = true
    var runHistory = true
    var achievements = true
    var achievementSound = true
    var achievementsToParty = false
    var autoImportLogs = true
    var runCoinsInstaSell = false
    /** Modification time of the newest archived log already imported. */
    var logsImportedUpTo = 0L
    var hideCaptureChat = false
    var hideGroundCapsules = true
    var hideFlyingCapsules = true
    var capsuleHideDistance = 2f
    var showWhere = 1
    val personalBests = SafariPersonalBests()
    private val flags = mapOf(::highlightTeammates to true, ::showTeammateNames to true, ::partyCommandsEnabled to true, ::useCloudApi to true, ::fullClearMode to false, ::sparklingMode to false, ::sparklingProfitableShardEsp to true, ::profitableShardEsp to true, ::timesaveOnly to true, ::warpAlertsEnabled to false,
        ::hideyhoQuestClicks to true, ::hideHauntedPaintings to true, ::shinyDetection to true,
        ::progressHud to true, ::countUniqueOnly to false, ::missingPanel to true,
        ::highlightBeeNests to true, ::removeDarkness to true, ::sparklingAlert to true,
        ::sparklingPartyAnnouncer to true, ::catchCountPanel to false, ::autoClicker to true, ::candleHitbox to true, ::holdManagerOnKick to true, ::downtimeCommand to true, ::honeyHives to true, ::honeyHivesOnlyIncomplete to true, ::honeyHiveRefillLabels to true, ::runHistory to true, ::achievements to true, ::achievementSound to true, ::achievementsToParty to false, ::autoImportLogs to true, ::runCoinsInstaSell to false,
        ::hideCaptureChat to false, ::hideGroundCapsules to true, ::hideFlyingCapsules to true,
        ::highlightSnooperWalls to true, ::showBeeNests to true, ::showMoundCount to true,
        ::showSnooperWalls to true, ::showMoundStats to false,
        ::allGemsAlert to true, ::allBirdFoodAlert to true, ::allIncenseAlert to true, ::runCompleteAlert to true)

    fun validCapsuleDistance(value: Float?) = value?.takeIf { it.isFinite() }?.coerceIn(.5f, 6f) ?: 2f
    fun validSparklingSound(value: String?) = value?.trim()?.let(Identifier::tryParse)?.toString() ?: "minecraft:block.amethyst_block.chime"

    fun init(configDirectory: Path) {
        configPath = configDirectory.resolve("sparkling-mutuals.properties")
        val properties = Properties()
        if (Files.exists(configPath)) Files.newInputStream(configPath).use(properties::load)
        apiKey = properties.getProperty("apiKey", "")
        sparklingSound = validSparklingSound(properties.getProperty("sparklingSound"))
        runCompleteTitle = properties.getProperty("runCompleteTitle", "Run Complete").take(120).ifBlank { "Run Complete" }
        runCompleteSound = properties.getProperty("runCompleteSound")?.trim()?.let(Identifier::tryParse)?.toString() ?: "minecraft:block.bell.use"
        runCompleteVolume = properties.getProperty("runCompleteVolume")?.toIntOrNull()?.coerceIn(0, 100) ?: 100
        sparklingSoundVolume = properties.getProperty("sparklingSoundVolume")?.toIntOrNull()?.coerceIn(0, 100) ?: 100
        flags.forEach { (property, default) -> property.set(properties.getProperty(property.name)?.toBooleanStrictOrNull() ?: default) }
        warpDelaySeconds = properties.getProperty("warpDelaySeconds", "25").toIntOrNull()?.coerceIn(1, 86400) ?: 25
        logsImportedUpTo = properties.getProperty("logsImportedUpTo")?.toLongOrNull() ?: 0L
        showWhere = properties.getProperty("showWhere")?.toIntOrNull()?.takeIf { it in 0..2 } ?: 1
        capsuleHideDistance = validCapsuleDistance(properties.getProperty("capsuleHideDistance")?.toFloatOrNull())
        SafariHud.load(properties)
        personalBests.load(properties)
        SafariFullClear.syncMode()
        SafariEspConfig.load(properties)
        SafariFullClear.applyEspPreset()
        AppearanceConfig.load(properties)
    }

    fun save() {
        Files.createDirectories(configPath.parent)
        Properties().apply {
            setProperty("apiKey", apiKey)
            setProperty("runCompleteTitle", runCompleteTitle)
            setProperty("runCompleteSound", runCompleteSound)
            setProperty("runCompleteVolume", runCompleteVolume.coerceIn(0, 100).toString())
            setProperty("sparklingSound", validSparklingSound(sparklingSound))
            setProperty("sparklingSoundVolume", sparklingSoundVolume.coerceIn(0, 100).toString())
            flags.forEach { (property, _) -> setProperty(property.name, property.get().toString()) }
            setProperty("warpDelaySeconds", warpDelaySeconds.toString())
            setProperty("logsImportedUpTo", logsImportedUpTo.toString())
            setProperty("showWhere", showWhere.coerceIn(0, 2).toString())
            setProperty("capsuleHideDistance", validCapsuleDistance(capsuleHideDistance).toString())
            SafariHud.save(this)
            personalBests.save(this)
            SafariEspConfig.save(this)
            AppearanceConfig.save(this)
            Files.newOutputStream(configPath).use { store(it, "Sparkling Mutuals configuration") }
        }
    }
}
