package net.johnceo.sparklingmutuals.contest

import com.mojang.blaze3d.pipeline.DepthStencilState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.CompareOp
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.fabricmc.loader.api.FabricLoader
import net.johnceo.sparklingmutuals.SparklingMutuals
import net.johnceo.sparklingmutuals.api.HypixelApi
import net.johnceo.sparklingmutuals.api.ApiProvider
import net.johnceo.sparklingmutuals.config.ConfigManager
import net.johnceo.sparklingmutuals.hud.SkyblockSidebar
import net.johnceo.sparklingmutuals.safari.EspBoxLines
import net.johnceo.sparklingmutuals.safari.SafariRules
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.blockentity.BeaconRenderer
import net.minecraft.client.renderer.rendertype.*
import net.minecraft.core.BlockPos
import net.minecraft.util.LightCoordsUtil
import net.fabricmc.fabric.api.event.player.AttackBlockCallback
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import org.joml.Matrix4f
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.util.Properties
import java.util.concurrent.Executors

/**
 * Beacons over the Torrhus Canyon honeyhives that are still full, while Miria's contest is unfinished — the contest
 * is the reason to collect honey, so they get out of the way once it's done. Each hive is tracked on its own by when
 * it refills ([HoneyHiveRules.state]): an hour after you loot it, or the API's refill time for a matched hive, and
 * only a Queen Bee proc refills one early. A loot belongs to the bee nest you clicked just before it, and a looted
 * nest that isn't known yet is added. Refill times are saved, with a countdown over each emptied hive.
 *
 * Positions ship in `honeyhives.txt` and more are learned from the hive build (oak fence below, oak trapdoor above).
 * The API names hives `hive_8` without saying where they are; a loot seen here at T binds the position to whichever
 * hive's refill time lands on T + 1h, and bindings are saved. Positions and ids are the same for everyone; loots and
 * refill times are per player and profile, so they are kept under the owner's scope (player UUID + the profile id
 * Hypixel announces on join) and an API answer for an earlier scope is thrown away.
 */
object HoneyHives {
    private val logger = LoggerFactory.getLogger("sparkling-mutuals/honeyhives")
    private val looted = Regex("You stick your hand into the honeyhive and feel around", RegexOption.IGNORE_CASE)
    private val queenBee = Regex("QUEEN BEE! The Honeyhive instantly refilled", RegexOption.IGNORE_CASE)
    private const val RANGE = 256.0
    private const val SCAN_RADIUS = 96
    private const val API_REFRESH_MILLIS = 5 * 60_000L
    private const val FULL = 0xFF55FF55.toInt()
    /** How soon after clicking a bee nest the loot message has to come to be that nest's. */
    private const val CLICK_WINDOW_MILLIS = 5_000L
    private const val EMPTY_LABEL = 0xFFFF6E6E.toInt()

    private val hives = linkedMapOf<String, Hive>()
    /** Scope -> hive key -> when it refills: the one thing that decides whether a hive is full. */
    private val refillAt = mutableMapOf<String, MutableMap<String, Long>>()
    /** Scope -> hive key -> Queen Bee refill time, which voids an earlier API refill time. */
    private val queenAt = mutableMapOf<String, MutableMap<String, Long>>()
    /** The last scope seen, saved, so loots apply straight away after a restart or a server switch. */
    private var lastScope: String? = null
    private var clickedNest: BlockPos? = null
    private var clickedAt = 0L
    /** Loots of still-unmatched hives, waiting for the same scope's API refill times. */
    private val pending = linkedMapOf<String, Long>()
    private var pendingScope: String? = null
    /** The API's refill times, null until known or when the profile has no honey data. */
    private var refills: Map<String, Long>? = null
    private var refillsScope: String? = null
    private var profile: String? = null
    private var scope: String? = null
    private var provider: ApiProvider? = null
    private var refilledAt = 0L
    private var refreshing = false
    private var visible = emptyList<Pair<Hive, Int>>()
    private var labels = emptyList<Pair<Hive, Long>>()
    private var ticks = 0
    private var level: Any? = null
    private var onTorrhus = false
    private var beamsBroken = false
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "Sparkling Mutuals honeyhives").apply { isDaemon = true } }
    private val file get() = FabricLoader.getInstance().configDir.resolve("sparkling-mutuals-honeyhives.properties")

    private val lines = RenderType.create("sparkling-mutuals:honeyhive_lines",
        RenderSetup.builder(RenderPipelines.register(RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(SparklingMutuals.id("pipeline/honeyhive_lines")).withCull(false)
            .withDepthStencilState(DepthStencilState(CompareOp.ALWAYS_PASS, false)).build()))
            .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
            .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET).createRenderSetup())

    fun register() {
        load()
        // Watching clicks only: neither callback changes or consumes the interaction.
        UseBlockCallback.EVENT.register { player, world, _, hit -> clicked(player, world, hit.blockPos); InteractionResult.PASS }
        AttackBlockCallback.EVENT.register { player, world, _, pos, _ -> clicked(player, world, pos); InteractionResult.PASS }
        ClientReceiveMessageEvents.GAME.register { message, overlay ->
            if (!overlay) Minecraft.getInstance().execute { chat(SafariRules.strip(message.string)) }
        }
        LevelRenderEvents.COLLECT_SUBMITS.register(::beams)
        LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(::outlines)
    }

    private fun chat(text: String) {
        if (text.startsWith("Party >") || text.startsWith("Guild >")) return
        HoneyHiveRules.profileId(text)?.let { profile = it; updateScope(Minecraft.getInstance()); return }
        if (!ConfigManager.honeyHives) return
        val client = Minecraft.getInstance()
        val owner = owner(client) ?: return
        val now = System.currentTimeMillis()
        val queen = queenBee.containsMatchIn(text)
        if (!queen && !looted.containsMatchIn(text)) return
        val hive = lootedHive(client, now) ?: return
        if (queen) {
            // Instantly refilled: full again now, whatever a loot or the API said.
            refillAt[owner]?.remove(hive.key); pending.remove(hive.key)
            queenAt.getOrPut(owner) { mutableMapOf() }[hive.key] = now
        } else {
            refillAt.getOrPut(owner) { mutableMapOf() }[hive.key] = now + HoneyHiveRules.REFILL_MILLIS
            queenAt[owner]?.remove(hive.key)
            if (hive.id == null) pending[hive.key] = now
            refilledAt = 0 // ask the API again soon, to match the hive's id
        }
        save()
    }

    private fun clicked(player: Player, world: Level, pos: BlockPos) {
        if (world.isClientSide && player === Minecraft.getInstance().player && world.getBlockState(pos).`is`(Blocks.BEE_NEST)) {
            clickedNest = pos.immutable(); clickedAt = System.currentTimeMillis()
        }
    }

    /** The bee nest clicked just before the message (added if new), or failing that the known hive nearest you. */
    private fun lootedHive(client: Minecraft, now: Long): Hive? {
        val pos = clickedNest?.takeIf { now - clickedAt <= CLICK_WINDOW_MILLIS }
        if (pos != null) return hives.getOrPut("${pos.x},${pos.y},${pos.z}") {
            logger.info("Honeyhive {} added from a loot", pos.toShortString()); Hive(pos.x, pos.y, pos.z)
        }
        val player = client.player ?: return null
        return HoneyHiveRules.nearest(hives.values, player.x, player.y, player.z)
    }

    /** Whose loots apply: the current scope, or until it's known again, the last one if it's the same player. */
    private fun owner(client: Minecraft): String? = scope
        ?: lastScope?.takeIf { last -> client.player?.uuid?.toString()?.replace("-", "")?.let { last.startsWith("$it.") } == true }

    /** A new player or profile: the previous owner's refill times and unmatched loots no longer apply. */
    private fun updateScope(client: Minecraft) {
        val now = HoneyHiveRules.scope(client.player?.uuid?.toString(), profile)
        ContestTracker.selectScope(now)
        val currentProvider = HypixelApi.provider
        if (provider != currentProvider) {
            provider = currentProvider; refills = null; refillsScope = null; refilledAt = 0
        }
        if (now == scope) return
        scope = now
        if (now != null && now != lastScope) { lastScope = now; save() }
        if (pendingScope != now) { pending.clear(); pendingScope = now }
        if (refillsScope != now) { refills = null; refillsScope = null }
        refilledAt = 0
    }

    fun tick(client: Minecraft) {
        // Only a disconnect forgets the profile: the player is briefly missing on every server switch too.
        if (client.connection == null) { profile = null; scope = null }
        if (client.level !== level) { level = client.level; onTorrhus = false; visible = emptyList(); labels = emptyList() }
        updateScope(client)
        if (++ticks % 20 != 0) return
        val world = client.level ?: return
        val player = client.player ?: return
        if (!ConfigManager.honeyHives || !SkyblockSidebar.inSkyblock(client)) { visible = emptyList(); labels = emptyList(); return }
        // The positions only mean anything on Torrhus Canyon: confirmed by finding a known hive where it should be.
        if (!onTorrhus) onTorrhus = hives.values.any { val pos = BlockPos(it.x, it.y, it.z)
            world.hasChunkAt(pos) && world.getBlockState(pos).`is`(Blocks.BEE_NEST) }
        if (!onTorrhus || (ConfigManager.honeyHivesOnlyIncomplete && ContestTracker.isComplete())) {
            visible = emptyList(); labels = emptyList(); return
        }
        if (ticks % 40 == 0) survey(client)
        val now = System.currentTimeMillis()
        if (now - refilledAt > API_REFRESH_MILLIS) refresh(client, now)
        refillAt.values.forEach { it.entries.removeIf { e -> now >= e.value } }
        queenAt.values.forEach { it.entries.removeIf { e -> now >= e.value + HoneyHiveRules.REFILL_MILLIS } }
        val refills = owner(client)?.let { refillAt[it] }.orEmpty()
        val shown = mutableListOf<Pair<Hive, Int>>()
        val counting = mutableListOf<Pair<Hive, Long>>()
        for (hive in hives.values) {
            if (player.distanceToSqr(hive.x + .5, hive.y + .5, hive.z + .5) > RANGE * RANGE) continue
            val refill = refills[hive.key]
            when (HoneyHiveRules.state(now, refill)) {
                HiveState.FULL -> shown += hive to FULL
                HiveState.EMPTY -> if (ConfigManager.honeyHiveRefillLabels) counting += hive to refill!!
            }
        }
        visible = shown
        labels = counting
    }

    /** Learns hives from the build around each one, using the palette to skip sections without a nest. */
    private fun survey(client: Minecraft) {
        val world = client.level ?: return
        val center = client.player?.blockPosition() ?: return
        var added = false
        for (cx in ((center.x - SCAN_RADIUS) shr 4)..((center.x + SCAN_RADIUS) shr 4))
            for (cz in ((center.z - SCAN_RADIUS) shr 4)..((center.z + SCAN_RADIUS) shr 4)) {
                val chunk = world.getChunk(cx, cz)
                chunk.sections.forEachIndexed { index, section ->
                    if (section.hasOnlyAir() || !section.maybeHas { it.`is`(Blocks.BEE_NEST) }) return@forEachIndexed
                    val bottom = chunk.minY + (index shl 4)
                    for (x in 0..15) for (y in 0..15) for (z in 0..15) {
                        if (!section.getBlockState(x, y, z).`is`(Blocks.BEE_NEST)) continue
                        val pos = BlockPos((cx shl 4) + x, bottom + y, (cz shl 4) + z)
                        if (world.getBlockState(pos.below()).`is`(Blocks.OAK_FENCE) && world.getBlockState(pos.above()).`is`(Blocks.OAK_TRAPDOOR)) {
                            val hive = Hive(pos.x, pos.y, pos.z)
                            if (hives.putIfAbsent(hive.key, hive) == null) added = true
                        }
                    }
                }
            }
        if (added) save()
    }

    /** Your refill times from the API, then any loots that can now be matched. Without a key the lookup just fails quietly. */
    private fun refresh(client: Minecraft, now: Long) {
        if (refreshing) return
        val uuid = client.player?.uuid?.toString() ?: return
        val asked = scope ?: return
        val askedProvider = HypixelApi.provider
        refreshing = true; refilledAt = now
        executor.submit {
            val result = runCatching { HypixelApi.getHoneyRefills(uuid) }
            client.execute {
                refreshing = false
                // A late answer for another player or profile is not this one's.
                if (asked != scope || askedProvider != HypixelApi.provider) { refilledAt = 0; return@execute }
                result.onFailure { logger.info("Honeyhive refill lookup failed: {}", it.message) }.onSuccess { response ->
                    // The selected profile changed since the Profile ID line: don't trust it for this scope.
                    if (response.profile != null && response.profile.replace("-", "") != profile?.replace("-", "")) return@onSuccess
                    refills = response.refills; refillsScope = asked
                    val found = response.refills ?: return@onSuccess
                    mergeRefills(asked, found)
                    if (pendingScope != asked) return@onSuccess
                    val bound = HoneyHiveRules.bind(pending, found, hives.values.mapNotNull { it.id }.toSet())
                    bound.forEach { (key, id) -> hives[key]?.id = id; pending.remove(key) }
                    pending.entries.removeIf { System.currentTimeMillis() - it.value > HoneyHiveRules.REFILL_MILLIS }
                    if (bound.isNotEmpty()) { logger.info("Honeyhive ids matched: {}", bound); save() }
                }
            }
        }
    }

    /** Gives every matched hive the API's refill time, unless a later loot or a Queen Bee says otherwise. */
    private fun mergeRefills(owner: String, found: Map<String, Long>) {
        val now = System.currentTimeMillis()
        val times = refillAt.getOrPut(owner) { mutableMapOf() }
        var changed = false
        for (hive in hives.values) {
            val api = hive.id?.let(found::get) ?: continue
            val merged = HoneyHiveRules.merge(times[hive.key], api, queenAt[owner]?.get(hive.key), now) ?: continue
            if (times.put(hive.key, merged) != merged) changed = true
        }
        if (changed) save()
    }

    // ------------------------------------------------------------------ drawing

    private fun beams(context: LevelRenderContext) {
        val client = Minecraft.getInstance()
        val world = client.level ?: return
        if (visible.isEmpty() || beamsBroken) return
        try {
            val camera = client.gameRenderer.mainCamera.position()
            val animation = Math.floorMod(world.gameTime, 40L) + client.deltaTracker.getGameTimeDeltaPartialTick(false)
            val poses = context.poseStack()
            for ((hive, color) in visible) {
                // Beams have no transparency, so an unknown hive's dimmer colour becomes a darker one.
                val rgb = if (color ushr 24 < 0xFF) (color and 0xFEFEFE) shr 1 else color and 0xFFFFFF
                poses.pushPose()
                poses.translate(hive.x - camera.x, hive.y - camera.y, hive.z - camera.z)
                BeaconRenderer.submitBeaconBeam(poses, context.submitNodeCollector(), BeaconRenderer.BEAM_LOCATION, 1f, animation,
                    0, BeaconRenderer.MAX_RENDER_Y, rgb, BeaconRenderer.SOLID_BEAM_RADIUS, BeaconRenderer.BEAM_GLOW_RADIUS)
                poses.popPose()
            }
        } catch (e: Throwable) {
            // If a future version moves the beacon renderer, fall back to drawn columns rather than nothing.
            beamsBroken = true
            logger.warn("Beacon beams unavailable, drawing columns instead", e)
        }
    }

    private fun outlines(context: LevelRenderContext) {
        val client = Minecraft.getInstance()
        if (client.level == null || client.options.hideGui || (visible.isEmpty() && labels.isEmpty())) return
        val camera = client.gameRenderer.mainCamera.position()
        val buffers = context.bufferSource()
        val vertices = buffers.getBuffer(lines)
        val pose = context.poseStack().last()
        fun box(minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double, color: Int) =
            EspBoxLines.edges((minX - camera.x).toFloat(), (minY - camera.y).toFloat(), (minZ - camera.z).toFloat(),
                (maxX - camera.x).toFloat(), (maxY - camera.y).toFloat(), (maxZ - camera.z).toFloat()) { x, y, z, ex, ey, ez, axis ->
                val nx = if (axis == 0) 1f else 0f; val ny = if (axis == 1) 1f else 0f; val nz = if (axis == 2) 1f else 0f
                vertices.addVertex(pose, x, y, z).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(2f)
                vertices.addVertex(pose, ex, ey, ez).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(2f)
            }
        for ((hive, color) in visible) {
            box(hive.x.toDouble(), hive.y.toDouble(), hive.z.toDouble(), hive.x + 1.0, hive.y + 1.0, hive.z + 1.0, color)
            if (beamsBroken) box(hive.x + .35, hive.y + 1.0, hive.z + .35, hive.x + .65, hive.y + 64.0, hive.z + .65, color)
        }
        buffers.endBatch(lines)
        val poses = context.poseStack()
        val now = System.currentTimeMillis()
        for ((hive, refillAt) in labels) {
            val text = HoneyHiveRules.countdown(refillAt - now)
            poses.pushPose(); poses.translate(hive.x + .5 - camera.x, hive.y + 1.4 - camera.y, hive.z + .5 - camera.z)
            poses.mulPose(client.gameRenderer.mainCamera.rotation()); poses.scale(.025f, -.025f, .025f)
            client.font.drawInBatch(text, -client.font.width(text) / 2f, 0f, EMPTY_LABEL, false, Matrix4f(poses.last().pose()),
                buffers, Font.DisplayMode.SEE_THROUGH, 0x40000000, LightCoordsUtil.FULL_BRIGHT)
            poses.popPose()
        }
    }

    // ------------------------------------------------------------------ storage

    private fun load() {
        javaClass.getResourceAsStream("/assets/sparkling-mutuals/honeyhives.txt")?.bufferedReader()?.use { reader ->
            HoneyHiveRules.parse(reader.readText()).forEach { hives[it.key] = it }
        }
        if (!Files.exists(file)) return
        val saved = Properties().apply { Files.newInputStream(file).use(::load) }
        val now = System.currentTimeMillis()
        saved.stringPropertyNames().forEach { name ->
            val value = saved.getProperty(name)
            when {
                name.startsWith("hive.") -> HoneyHiveRules.parse(name.removePrefix("hive.").replace(',', ' ') + " " + value)
                    .firstOrNull()?.let { hive -> hives.merge(hive.key, hive) { old, new -> old.apply { id = id ?: new.id } } }
                name.startsWith("refill.") -> HoneyHiveRules.parseLoot(name, value, "refill")
                    ?.takeIf { (_, _, at) -> now < at }
                    ?.let { (owner, key, at) -> refillAt.getOrPut(owner) { mutableMapOf() }[key] = at }
                name.startsWith("queen.") -> HoneyHiveRules.parseLoot(name, value, "queen")
                    ?.takeIf { (_, _, at) -> now < at + HoneyHiveRules.REFILL_MILLIS }
                    ?.let { (owner, key, at) -> queenAt.getOrPut(owner) { mutableMapOf() }[key] = at }
                // Older files kept the loot time.
                name.startsWith("loot.") -> HoneyHiveRules.parseLoot(name, value)
                    ?.takeIf { (_, _, at) -> now < at + HoneyHiveRules.REFILL_MILLIS }
                    ?.let { (owner, key, at) -> refillAt.getOrPut(owner) { mutableMapOf() }[key] = at + HoneyHiveRules.REFILL_MILLIS }
                name == "scope.last" -> lastScope = value.takeIf { it.isNotBlank() }
            }
        }
    }

    private fun save() {
        val out = Properties()
        hives.values.forEach { out.setProperty("hive.${it.key}", it.id ?: "-") }
        refillAt.forEach { (owner, times) -> times.forEach { (key, at) -> out.setProperty("refill.$owner.$key", at.toString()) } }
        queenAt.forEach { (owner, times) -> times.forEach { (key, at) -> out.setProperty("queen.$owner.$key", at.toString()) } }
        lastScope?.let { out.setProperty("scope.last", it) }
        runCatching { Files.createDirectories(file.parent); Files.newOutputStream(file).use { out.store(it, "Sparkling Mutuals honeyhives") } }
            .onFailure { logger.warn("Couldn't save honeyhives", it) }
    }
}
