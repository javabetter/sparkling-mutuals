package net.johnceo.sparklingmutuals.mixin

import net.johnceo.sparklingmutuals.safari.SafariAssist
import net.johnceo.sparklingmutuals.config.AppearanceConfig
import net.johnceo.sparklingmutuals.config.SafariEspConfig
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.world.entity.Entity
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(EntityRenderer::class)
class SafariShinyMixin {
    @Inject(method = ["extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V"], at = [At("TAIL")])
    private fun highlight(entity: Entity, state: EntityRenderState, delta: Float, info: CallbackInfo) {
        if (net.johnceo.sparklingmutuals.safari.TeammateEsp.isTeammate(entity)) {
            if (net.johnceo.sparklingmutuals.config.ConfigManager.highlightTeammates)
                state.outlineColor = net.johnceo.sparklingmutuals.safari.TeammateEsp.COLOR
            if (net.johnceo.sparklingmutuals.config.ConfigManager.showTeammateNames) state.nameTag = null
        }
        if (SafariAssist.isHighlighted(entity.id)) state.outlineColor = SafariEspConfig.rgb(AppearanceConfig.sparklingColor)
    }
}
