package net.johnceo.sparklingmutuals.safari

import com.mojang.blaze3d.pipeline.DepthStencilState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.CompareOp
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.fabricmc.fabric.api.event.player.AttackBlockCallback
import net.johnceo.sparklingmutuals.SparklingMutuals
import net.johnceo.sparklingmutuals.config.ConfigManager
import net.johnceo.sparklingmutuals.config.AppearanceConfig
import net.johnceo.sparklingmutuals.config.SafariEspConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.rendertype.*
import net.minecraft.core.BlockPos
import net.minecraft.util.LightCoordsUtil
import net.minecraft.world.InteractionResult
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.chunk.status.ChunkStatus
import org.joml.Matrix4f

/** v0.9.0 NestTracker/WaypointRenderer, skipping loaded chunk sections whose palettes have no nests. */
object BeeNests {
    private val known = mutableSetOf<BlockPos>()
    private val punched = mutableSetOf<BlockPos>()
    private var ticks = 0
    private val handledByParty get() = SafariTracking.ledger.current?.partyNestsHandled == true
    val unpunchedCount get() = if (handledByParty && SafariFullClear.mode == SafariMode.SPARKLING) 0 else known.count { it !in punched }
    val punchedCount get() = punched.size
    val allChecked get() = (known.isNotEmpty() || punched.isNotEmpty()) && known.all { it in punched }
    private val lines = RenderType.create("sparkling-mutuals:nests", RenderSetup.builder(RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.LINES_SNIPPET).withLocation(SparklingMutuals.id("pipeline/nests"))
            .withDepthStencilState(DepthStencilState(CompareOp.ALWAYS_PASS, false)).build()))
        .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING).setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET).createRenderSetup())
    private val corners = List(8) { i -> floatArrayOf(if (i and 1 == 0) -.005f else 1.005f,
        if (i and 2 == 0) -.005f else 1.005f, if (i and 4 == 0) -.005f else 1.005f) }

    fun register() {
        AttackBlockCallback.EVENT.register { _, level, _, pos, _ ->
            if (level.isClientSide && SafariAssist.inSafari && level.getBlockState(pos).`is`(Blocks.BEE_NEST)) {
                val nest = pos.immutable()
                known.add(nest); punched.add(nest)
            }
            InteractionResult.PASS
        }
        LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(::render)
    }
    fun tick(client: Minecraft) {
        if (++ticks < 40) return
        ticks = 0
        val level = client.level ?: return
        val centre = client.player?.blockPosition() ?: return
        if (SafariAssist.biome != SafariBiome.FOREST) return
        for (x in ((centre.x - 24) shr 4)..((centre.x + 24) shr 4)) for (z in ((centre.z - 24) shr 4)..((centre.z + 24) shr 4)) {
            val chunk = level.getChunk(x, z, ChunkStatus.FULL, false) as? LevelChunk ?: continue
            chunk.sections.forEachIndexed { index, section ->
                val bottom = chunk.getSectionYFromSectionIndex(index) shl 4
                val lowY = maxOf(bottom, centre.y - 12)
                val highY = minOf(bottom + 15, centre.y + 12)
                if (lowY <= highY && section.maybeHas { it.`is`(Blocks.BEE_NEST) }) {
                    for (bx in maxOf(x shl 4, centre.x - 24)..minOf((x shl 4) + 15, centre.x + 24))
                        for (bz in maxOf(z shl 4, centre.z - 24)..minOf((z shl 4) + 15, centre.z + 24))
                            for (by in lowY..highY) if (section.getBlockState(bx and 15, by and 15, bz and 15).`is`(Blocks.BEE_NEST))
                                known.add(BlockPos(bx, by, bz))
                }
            }
        }
        known.removeAll { level.isLoaded(it) && !level.getBlockState(it).`is`(Blocks.BEE_NEST) }
    }
    private fun render(context: LevelRenderContext) {
        val client = Minecraft.getInstance()
        val level = client.level ?: return
        if (client.player == null || client.options.hideGui || !SafariAssist.inSafari) return
        val camera = client.gameRenderer.mainCamera.position()
        val poses = context.poseStack()
        val buffers = context.bufferSource()
        val vertices = buffers.getBuffer(lines)
        val markers = when {
            SafariAssist.biome == SafariBiome.FOREST &&
                SafariHelperRules.showNests(SafariFullClear.mode, SafariTracking.ledger.current, SafariSparklingMode.state) ->
                known.filter { it !in punched }.map { Triple(it, "Nest", SafariEspConfig.rgb(AppearanceConfig.nestColor)) }
            SafariAssist.biome == SafariBiome.CAVERN &&
                SafariHelperRules.needed("Snoozle", SafariFullClear.mode, SafariTracking.ledger.current, SafariSparklingMode.state) ->
                SafariStructures.intactWalls.map { Triple(it, "Snooper wall", SafariEspConfig.rgb(AppearanceConfig.snooperColor)) }
            else -> emptyList()
        }.filter { level.isLoaded(it.first) && !level.getBlockState(it.first).isAir && it.first.distToCenterSqr(camera) < 40000 }
        for ((pos, _, color) in markers) {
            poses.pushPose(); poses.translate(pos.x - camera.x, pos.y - camera.y, pos.z - camera.z)
            val pose = poses.last()
            corners.forEachIndexed { i, a ->
                for (bit in listOf(1, 2, 4)) if (i and bit == 0) {
                    val b = corners[i or bit]
                    val axis = if (bit == 1) 0 else if (bit == 2) 1 else 2
                    repeat(2) { endpoint ->
                        val point = if (endpoint == 0) a else b
                        vertices.addVertex(pose, point[0], point[1], point[2]).setColor(color)
                            .setNormal(pose, if (axis == 0) 1f else 0f, if (axis == 1) 1f else 0f, if (axis == 2) 1f else 0f).setLineWidth(3f)
                    }
                }
            }
            poses.popPose()
        }
        buffers.endBatch(lines)
        for ((pos, label, color) in markers) {
            val text = "$label · ${kotlin.math.sqrt(pos.distToCenterSqr(camera)).toInt()}m"
            poses.pushPose(); poses.translate(pos.x + .5 - camera.x, pos.y + 1.4 - camera.y, pos.z + .5 - camera.z)
            poses.mulPose(client.gameRenderer.mainCamera.rotation()); poses.scale(.025f, -.025f, .025f)
            client.font.drawInBatch(text, -client.font.width(text) / 2f, 0f, color, false,
                Matrix4f(poses.last().pose()), buffers, Font.DisplayMode.SEE_THROUGH, 0x40000000, LightCoordsUtil.FULL_BRIGHT)
            poses.popPose()
        }
    }
    fun reset() { known.clear(); punched.clear(); ticks = 0 }
}
