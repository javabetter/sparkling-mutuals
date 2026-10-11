package net.johnceo.sparklingmutuals.mixin

import net.johnceo.sparklingmutuals.safari.SafariManagerGuard
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.EntityHitResult
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

/** Right-clicking an entity has no client-side Fabric event; returning FAIL here means no interact packet is sent. */
@Mixin(MultiPlayerGameMode::class)
class SafariManagerMixin {
    @Inject(method = ["interact"], at = [At("HEAD")], cancellable = true)
    private fun guard(player: Player, entity: Entity, hit: EntityHitResult, hand: InteractionHand, info: CallbackInfoReturnable<InteractionResult>) {
        if (SafariManagerGuard.shouldBlock(entity)) info.returnValue = InteractionResult.FAIL
    }
}
