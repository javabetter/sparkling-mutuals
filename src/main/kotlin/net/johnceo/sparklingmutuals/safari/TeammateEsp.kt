package net.johnceo.sparklingmutuals.safari

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.johnceo.sparklingmutuals.config.ConfigManager
import net.johnceo.sparklingmutuals.hud.SkyblockSidebar
import net.johnceo.sparklingmutuals.party.PartyManager
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.util.LightCoordsUtil
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import org.joml.Matrix4f

/** Noamm-style outlines and distance-scaled names, using SkyBlock party UUIDs rather than dungeon classes. */
object TeammateEsp {
    const val COLOR = 0xFF55FF55.toInt()
    fun isTeammate(entity: Entity): Boolean {
        val client = Minecraft.getInstance()
        return entity is Player && !entity.isRemoved && client.level?.getEntity(entity.id) === entity &&
            TeammateEspRules.eligible(SkyblockSidebar.inSkyblock(client), PartyManager.hasInfo,
                entity.uuid, client.player?.uuid, PartyManager.getMembers())
    }
    fun register() {
        LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register { context ->
            if (!ConfigManager.showTeammateNames) return@register
            val client = Minecraft.getInstance()
            val world = client.level ?: return@register
            if (!PartyManager.hasInfo || !SkyblockSidebar.inSkyblock(client)) return@register
            val camera = client.gameRenderer.mainCamera.position()
            val poses = context.poseStack()
            val buffers = context.bufferSource()
            val delta = client.deltaTracker.getGameTimeDeltaPartialTick(false)
            for (player in world.players()) {
                if (!isTeammate(player)) continue
                val pos = player.getPosition(delta)
                val distance = pos.distanceTo(camera)
                val scale = (.025 * TeammateEspRules.nameScale(distance)).toFloat()
                val text = player.gameProfile.name
                poses.pushPose()
                poses.translate(pos.x - camera.x, pos.y + player.bbHeight + .7 + distance * .015 - camera.y, pos.z - camera.z)
                poses.mulPose(client.gameRenderer.mainCamera.rotation())
                poses.scale(scale, -scale, scale)
                client.font.drawInBatch(text, -client.font.width(text) / 2f, 0f, COLOR, false,
                    Matrix4f(poses.last().pose()), buffers, Font.DisplayMode.SEE_THROUGH, 0x40000000, LightCoordsUtil.FULL_BRIGHT)
                poses.popPose()
            }
        }
    }
}
