package net.johnceo.sparklingmutuals.safari

import java.util.UUID

import com.mojang.authlib.GameProfile
import com.mojang.blaze3d.pipeline.DepthStencilState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.CompareOp
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.johnceo.sparklingmutuals.SparklingMutuals
import net.johnceo.sparklingmutuals.config.SafariEspConfig
import net.johnceo.sparklingmutuals.config.ConfigManager
import net.minecraft.client.gui.Font
import net.minecraft.util.LightCoordsUtil
import org.joml.Matrix4f
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.rendertype.*
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.Display
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.animal.fish.TropicalFish
import net.minecraft.world.entity.animal.parrot.Parrot
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.monster.Shulker
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.ShulkerBoxBlock
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.slf4j.LoggerFactory

data class EspCritterObservation(val id: Int, val species: String, val biome: SafariBiome, val x: Double, val y: Double, val z: Double, val mound: Boolean, val uuid: UUID)
data class SafariEspObservations(val critters: List<EspCritterObservation>, val floorDrops: List<EspDrop>, val scanned: Boolean = false)

/** Nebulune SafariESP identification and box offsets, adapted to our Fabric renderer without Athen. */
object SafariEsp {
    private data class Target(val entity: Entity, val species: String, val shulker: Boolean)
    private var targets = emptyList<Target>()
    private var drops = emptyList<EspDrop>()
    private var level: Any? = null
    private var ticks = 0
    private var scanned = false
    private var stringDisplays = emptyList<EspDrop>()
    private val captures = EspCaptureMemory()
    private val modelLabels = EspModelLabels()
    private val motion = EspMotion()
    private val litterbugs = LitterbugProjection()
    private val shyworms = ShywormProjection()
    private val logger = LoggerFactory.getLogger("sparkling-mutuals/esp")
    private fun renderType(name: String, snippet: RenderPipeline.Snippet) = RenderType.create("sparkling-mutuals:$name",
        RenderSetup.builder(RenderPipelines.register(RenderPipeline.builder(snippet)
            .withLocation(SparklingMutuals.id("pipeline/$name"))
            .withCull(false)
            .withDepthStencilState(DepthStencilState(CompareOp.ALWAYS_PASS, false)).build()))
            .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
            .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET).createRenderSetup())
    private val lines = renderType("safari_esp", RenderPipelines.LINES_SNIPPET)
    private val fill = renderType("safari_drops", RenderPipelines.DEBUG_FILLED_SNIPPET)

    fun register() {
        LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(::render)
        UseItemCallback.EVENT.register { player, world, hand ->
            val client = Minecraft.getInstance()
            val id = player.getItemInHand(hand).get(DataComponents.CUSTOM_DATA)?.copyTag()?.getStringOr("id", "")
            if (world === client.level && player === client.player && SafariAssist.inSafari &&
                id in setOf("CRITTER_CAPSULE", "MASTERFUL_CRITTER_CAPSULE")) {
                rememberAim(client)
                captures.throwing(System.currentTimeMillis())
            }
            InteractionResult.PASS
        }
    }
    private fun texture(profile: GameProfile?) = SafariEspRules.textureHash(profile?.properties()?.get("textures")?.firstOrNull()?.value())
    private fun texture(stack: ItemStack) = texture(stack.get(DataComponents.PROFILE)?.partialProfile())
    private fun describe(entity: Entity): EspEntity {
        val item = (entity as? Display.ItemDisplay)?.itemStack
        val shulker = when (entity) {
            is Shulker -> entity.color
            is Display.BlockDisplay -> (entity.blockState.block as? ShulkerBoxBlock)?.color
            is Display.ItemDisplay -> (Block.byItem(entity.itemStack.item) as? ShulkerBoxBlock)?.color
            else -> null
        }
        val texture = when (entity) {
            is ArmorStand -> texture(entity.getItemBySlot(EquipmentSlot.HEAD))
            is Display.ItemDisplay -> texture(item!!)
            is Player -> texture(entity.gameProfile)
            else -> null
        }
        return EspEntity(entity.type.toShortString(), texture, shulker?.name,
            (entity as? TropicalFish)?.let { "${it.pattern.name}/${it.baseColor.name}/${it.patternColor.name}" },
            (entity as? Parrot)?.variant?.name, entity.isInvisible || !modelVisible(entity), entity.passengers.isNotEmpty())
    }
    fun isRockmiteMound(entity: Entity, client: Minecraft): Boolean {
        if (entity.isRemoved || client.level?.getEntity(entity.id) !== entity || !SafariAssist.inSafari) return false
        return entity is Display.ItemDisplay && SafariEspRules.identify(describe(entity))?.name == "Rockmite" ||
            entity.type == net.minecraft.world.entity.EntityType.INTERACTION && SafariStructures.isMound(entity, client)
    }
    private fun modelVisible(entity: Entity): Boolean {
        if (entity !is Display) return true
        val scale = entity.renderState()?.transformation()?.get(1f)?.scale() ?: return false
        return entity.shouldRenderAtSqrDistance(0.0) && SafariEspRules.modelVisible(scale.x(), scale.y(), scale.z())
    }
    /** Keep per-species aim; server chat names the target after the original model can disappear. */
    private fun rememberAim(client: Minecraft) {
        val player = client.player ?: return
        val eye = player.eyePosition
        val look = player.lookAngle
        val candidates = targets.filter { live(it, client, forEsp = true) }.map {
            val offset = bounds(it, 1f).center.subtract(eye)
            val along = offset.dot(look)
            EspCaptureCandidate(it.entity.id, it.species, SafariEspRules.captureModel(it.entity.type.toShortString()),
                it.species == "Rockmite" && it.entity is Display.ItemDisplay,
                player.distanceToSqr(it.entity), if (along < 0) Double.POSITIVE_INFINITY else (offset.lengthSqr() - along * along).coerceAtLeast(0.0))
        }
        val now = System.currentTimeMillis()
        candidates.filter { it.aimSquared <= 16 }.groupBy { it.species }.forEach { (species, aimed) ->
            val id = SafariEspRules.capturedDisplay(species, aimed)
            targets.firstOrNull { it.entity.id == id }?.let { captures.sighted(it.entity.uuid, species, now) }
        }
    }
    fun threw(species: String) { captures.threw(species, System.currentTimeMillis()) }
    fun shywormMessage(text: String, now: Long) {
        val player = Minecraft.getInstance().player ?: return
        shyworms.chat(text, now, player.x, player.z)
    }
    fun escaped(species: String) { captures.escaped(species, System.currentTimeMillis())?.let(modelLabels::release) }
    fun caught(species: String) {
        val id = captures.caught(species, System.currentTimeMillis())
        if (id == null) {
            if (targets.any { it.species == species && trackedModel(it) })
                logger.info("Capture of {} had no throw/model association; checking name-tag lifecycle instead.", species)
            return
        }
        logger.info("Retired captured {} model {}.", species, id)
        targets = targets.filterNot { it.entity.uuid == id }
    }
    fun clearCaptured() { captures.reset(); modelLabels.reset(); motion.reset(); litterbugs.reset(); shyworms.reset() }
    fun tick(client: Minecraft) {
        if (level !== client.level) { reset(); level = client.level }
        SafariFloorDrops.state.visit(SafariAssist.biome)
        if (!SafariAssist.inSafari || client.player == null || client.level == null) {
            targets = emptyList(); drops = emptyList(); stringDisplays = emptyList(); ticks = 0; scanned = false; return
        }
        rememberAim(client)
        if (++ticks < 5) return
        ticks = 0
        val strings = mutableListOf<EspDrop>()
        val entities = client.level!!.entitiesForRendering().filter { !it.isRemoved && client.level!!.getEntity(it.id) === it }.toList()
        val labels = entities.mapNotNull { entity ->
            entity.customName?.string?.let(SafariEspRules::labelSpecies)?.let { name -> entity to name }
        }.groupBy({ it.second }, { it.first })
        targets = entities.filterNot { captures.hidden(it.uuid) }.mapNotNull { entity ->
            if (entity is Display.ItemDisplay && !entity.isInvisible && entity.itemStack.`is`(Items.STRING)) {
                val pos = entity.blockPosition()
                strings.add(EspDrop(entity.id, pos.x, pos.y, pos.z))
                return@mapNotNull null
            }
            val mob = SafariEspRules.identify(describe(entity)) ?: return@mapNotNull null
            val descriptor = mob.identifiers.any { it.shulker != null }
            Target(entity, mob.name, descriptor)
        }
        val now = System.currentTimeMillis()
        motion.retain(targets.map { it.entity.uuid }.toSet())
        targets.forEach { target ->
            val e = target.entity
            motion.observe(e.uuid, target.species, e.x, e.y, e.z, client.player!!.distanceToSqr(e), now)
            if (target.species == "Litterbug") litterbugs.observe(e.uuid, e.y, now)
        }
        targets.filter(::trackedModel).forEach { target ->
            val nearby = labels[target.species].orEmpty().filter { it.distanceToSqr(target.entity) <= 9 }
            val separate = nearby.filter { it !== target.entity }
            modelLabels.observe(target.entity.uuid, target.species, (separate.ifEmpty { nearby }).map {
                EspModelLabel(it.uuid, target.species, it.distanceToSqr(target.entity), it.isCustomNameVisible)
            }, now)
        }
        val worms = targets.filter { it.species == "Shyworm" }
        shyworms.retain(worms.map { it.entity.uuid }.toSet())
        worms.forEach {
            val e = it.entity
            shyworms.observe(e.uuid, e.x, e.y, e.z,
                modelLabels.label(e.uuid) != null && modelLabels.current(e.uuid, now), now)
        }
        shyworms.reconcile(now)
        targets = targets.filter { live(it, client, forEsp = true) }
        stringDisplays = strings.toList()
        drops = SafariEspRules.floorDrops(strings)
        scanned = true
        rememberAim(client)
    }
    private fun trackedModel(target: Target) = SafariEspRules.requiresModelLabel(target.species, target.entity.type.toShortString())
    private fun live(target: Target, client: Minecraft, forEsp: Boolean = false): Boolean {
        val entity = target.entity
        val current = SafariEspRules.targetCurrent(target.species, describe(entity), entity.isRemoved,
            !captures.hidden(entity.uuid) && client.level?.getEntity(entity.id) === entity, entity.level() === client.level)
        val now = System.currentTimeMillis()
        val moving = motion.current(entity.uuid, now)
        val labeled = !trackedModel(target) || modelLabels.current(entity.uuid, now)
        return if (forEsp) SafariEspRules.renderCurrent(target.species, ConfigManager.sparklingMode, current, moving, labeled,
            if (ConfigManager.sparklingMode) ConfigManager.sparklingProfitableShardEsp
            else !ConfigManager.fullClearMode && ConfigManager.profitableShardEsp)
            else current && moving && labeled
    }
    fun debug(client: Minecraft) {
        val player = client.player ?: return
        val entities = client.level?.entitiesForRendering()?.filter { !it.isRemoved && player.distanceToSqr(it) <= 6400 }?.toList() ?: return
        logger.info("Safari ESP diagnostics: biome={}, fullClear={}, collected={}", SafariAssist.biome,
            ConfigManager.fullClearMode, SafariRoster.all.associate { it.name to (SafariTracking.run?.count(it.name) ?: 0) })
        entities.mapNotNull { entity -> SafariEspRules.identify(describe(entity))?.let { entity to it.name } }.forEach { (entity, species) ->
            val tag = modelLabels.label(entity.uuid)
            val label = entities.firstOrNull { it.uuid == tag }
            logger.info("ESP model species={} id={} uuid={} type={} pos={} retired={} label={} labelName={} labelVisible={} labelDistance={} labelCurrent={} descriptor={} vehicle={} passengers={} modelName={} nearbyLabels={}",
                species, entity.id, entity.uuid, entity.type.toShortString(), entity.position(), captures.hidden(entity.uuid), tag,
                label?.customName?.string, label?.isCustomNameVisible, label?.distanceToSqr(entity),
                modelLabels.current(entity.uuid, System.currentTimeMillis()), describe(entity), entity.vehicle?.id, entity.passengers.map { it.id }, entity.customName?.string,
                entities.filter { it.customName != null && it.distanceToSqr(entity) <= 9 }.map { "${it.id}:${it.customName?.string}:${it.isCustomNameVisible}" })
        }
    }
    /** Loaded server entities only; observations do not depend on ESP switches or biome filtering. */
    fun observations(): SafariEspObservations {
        val client = Minecraft.getInstance()
        if (!SafariAssist.inSafari || client.level == null || client.level !== level) return SafariEspObservations(emptyList(), emptyList())
        val critters = targets.filter { live(it, client) }.mapNotNull { target ->
            val e = target.entity
            val biome = SafariEspRules.biomeAt(e.x, e.z) ?: return@mapNotNull null
            if (SafariRoster.named(target.species)?.biome != biome) return@mapNotNull null
            EspCritterObservation(e.id, target.species, biome, e.x, e.y, e.z, target.species == "Rockmite" && e is Display.ItemDisplay, e.uuid)
        }
        return SafariEspObservations(critters, liveDrops(client), scanned)
    }
    private fun liveDrops(client: Minecraft): List<EspDrop> {
        val strings = stringDisplays.filter { drop ->
            val entity = client.level?.getEntity(drop.id) as? Display.ItemDisplay ?: return@filter false
            val p = entity.blockPosition()
            !entity.isRemoved && !entity.isInvisible && entity.itemStack.`is`(Items.STRING) &&
                p.x == drop.x && p.y == drop.y && p.z == drop.z
        }
        return SafariEspRules.floorDrops(strings)
    }
    private fun bounds(target: Target, delta: Float): AABB {
        val e = target.entity
        val box = when {
            e is Display && target.shulker -> AABB(e.x - .5, e.y, e.z - .5, e.x + .5, e.y + 1, e.z + .5)
            e is Display -> AABB(e.x - .3, e.y - .45, e.z - .3, e.x + .3, e.y + .15, e.z + .3)
            e is ArmorStand -> AABB(e.x - .3, e.y + 1.35, e.z - .3, e.x + .3, e.y + 1.95, e.z + .3)
            else -> e.boundingBox
        }
        return EspBoxSize.padded(box.move(e.getPosition(delta).subtract(e.position())))
    }
    private fun render(context: LevelRenderContext) {
        val client = Minecraft.getInstance()
        if (!SafariAssist.inSafari || client.player == null || client.level == null || client.options.hideGui) return
        val camera = client.gameRenderer.mainCamera.position()
        val playerBiome = SafariAssist.biome
        val poses = context.poseStack()
        val buffers = context.bufferSource()
        val vertices = buffers.getBuffer(lines)
        val pose = poses.last()
        fun frame(box: AABB, color: Int) {
            val b = box.move(-camera.x, -camera.y, -camera.z)
            EspBoxLines.edges(b.minX.toFloat(), b.minY.toFloat(), b.minZ.toFloat(),
                b.maxX.toFloat(), b.maxY.toFloat(), b.maxZ.toFloat()) { x, y, z, ex, ey, ez, axis ->
                val nx = if (axis == 0) 1f else 0f
                val ny = if (axis == 1) 1f else 0f
                val nz = if (axis == 2) 1f else 0f
                vertices.addVertex(pose, x, y, z).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(2f)
                vertices.addVertex(pose, ex, ey, ez).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(2f)
            }
        }
        val delta = client.deltaTracker.getGameTimeDeltaPartialTick(false)
        val now = System.currentTimeMillis()
        val timers = mutableListOf<Triple<String, Int, AABB>>()
        targets = targets.filter { live(it, client, forEsp = true) }
        for (target in targets) {
            val e = target.entity
            val mob = SafariRoster.named(target.species)!!
            val group = SafariEspConfig.groups.getValue(mob.biome.name.lowercase())
            val mobBiome = SafariEspRules.biomeAt(e.x, e.z)
            val needed = if (ConfigManager.sparklingMode)
                SafariEspRules.neededForSparkling(mob.name, mob.name == "Rockmite" && e is Display.ItemDisplay,
                    SafariSparklingMode.state, ConfigManager.sparklingProfitableShardEsp, SafariTracking.ledger.current, e.uuid, now)
            else SafariEspRules.neededForRun(mob.name, e is Display.ItemDisplay, SafariTracking.ledger.current, ConfigManager.fullClearMode,
                ConfigManager.profitableShardEsp, SafariSparklingMode.state)
            if (needed &&
                SafariEspConfig.entityEnabled(mob.name, e is Display.ItemDisplay) && mobBiome == mob.biome && SafariEspRules.visible(true, group.enabled, group.onlyInBiome, playerBiome, mobBiome)) {
                val box = bounds(target, delta)
                val height = when (mob.name) {
                    "Litterbug" -> litterbugs.hiddenHeight(e.uuid, e.x, e.y, e.z)
                    "Shyworm" -> shyworms.hiddenHeight(e.uuid)
                    else -> null
                }
                val projected = if (height == null) box else box.move(0.0, height - e.getPosition(delta).y, 0.0)
                frame(projected, SafariEspConfig.rgb(SafariEspConfig.entityColor(mob.name, e is Display.ItemDisplay)))
                if (mob.name == "Litterbug" && height != null) litterbugs.hiddenMarker(e.uuid, e.x, e.y, e.z, now)?.let {
                    timers.add(Triple(it.text, it.color, projected))
                }
                if (mob.name == "Shyworm" && !ConfigManager.sparklingMode) {
                    shyworms.timer(e.uuid, now)?.let { timers.add(Triple(it.text, it.color, projected)) }
                    shyworms.path(e.uuid, now)?.let {
                        val y = it.height + .02
                        frame(AABB(it.minX, y, it.minZ, it.maxX, y, it.maxZ), 0xFFFF0000.toInt())
                    }
                }
            }
        }
        val floor = SafariEspConfig.groups.getValue("floor")
        val tiles = liveDrops(client).filter {
            val biome = SafariEspRules.biomeAt(it.x.toDouble(), it.z.toDouble())
            SafariEspRules.visible(true, SafariFloorDrops.enabled(biome), floor.onlyInBiome, playerBiome, biome)
        }
        val color = SafariEspConfig.rgb(SafariEspConfig.floorColor)
        tiles.forEach { frame(AABB(it.x.toDouble(), it.y + 1.0, it.z.toDouble(), it.x + 1.0, it.y + 1.0, it.z + 1.0), color) }
        buffers.endBatch(lines)
        val quads = buffers.getBuffer(fill)
        for (tile in tiles) {
            val x = (tile.x - camera.x).toFloat(); val y = (tile.y + 1 - camera.y).toFloat(); val z = (tile.z - camera.z).toFloat()
            for ((dx, dz) in listOf(0f to 0f, 0f to 1f, 1f to 1f, 1f to 0f))
                quads.addVertex(pose, x + dx, y, z + dz).setColor(color and 0xFFFFFF or ((color ushr 25) shl 24))
        }
        buffers.endBatch(fill)
        for ((text, color, box) in timers) {
            poses.pushPose(); poses.translate(box.center.x - camera.x, box.maxY + .2 - camera.y, box.center.z - camera.z)
            poses.mulPose(client.gameRenderer.mainCamera.rotation()); poses.scale(.025f, -.025f, .025f)
            client.font.drawInBatch(text, -client.font.width(text) / 2f, 0f, color, false,
                Matrix4f(poses.last().pose()), buffers, Font.DisplayMode.SEE_THROUGH, 0x40000000, LightCoordsUtil.FULL_BRIGHT)
            poses.popPose()
        }
    }
    fun reset() { targets = emptyList(); drops = emptyList(); level = null; ticks = 0; stringDisplays = emptyList(); scanned = false; clearCaptured() }
}
