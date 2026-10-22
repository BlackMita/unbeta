package net.unbeta.hg.mixin;

import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.unbeta.hg.Restrictions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Restricted players don't pick items up. */
@Mixin(ItemEntity.class)
public abstract class RestrictedPickupMixin {

    @Inject(method = "onPlayerCollision", at = @At("HEAD"), cancellable = true)
    private void unbetaHg_noPickup(PlayerEntity player, CallbackInfo ci) {
        if (Restrictions.held(player)) ci.cancel();
    }
}
