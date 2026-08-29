package net.unbeta.content.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.unbeta.content.unlikelike.UnlikeLikeEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

@Mixin(PlayerEntity.class)
public class UnlikeLikeDismountMixin {

    @Inject(method = "dismountVehicle", at = @At("HEAD"), cancellable = true)
    private void unbeta_blockUnlikeLikeDismount(CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity)(Object)this;
        if (self.getVehicle() instanceof UnlikeLikeEntity ul) {
            // Allow dismount if: Unlike-Like is dead, ejecting intentionally, or player is dead
            if (!ul.isDead() && !ul.ejecting && !self.isDead()) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void unbeta_noAttackWhileGrabbed(Entity target, CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity)(Object)this;
        if (self.getVehicle() instanceof UnlikeLikeEntity) {
            ci.cancel();
        }
    }
}