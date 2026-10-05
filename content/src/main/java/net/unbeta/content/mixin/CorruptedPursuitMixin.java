package net.unbeta.content.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.unbeta.content.corruption.CorruptedAnimal;
import net.unbeta.content.corruption.PursuitSpeed;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Corrupted animals speed up while chasing, and the ramp resets when they land a hit. */
@Mixin(MobEntity.class)
public abstract class CorruptedPursuitMixin {

    @Inject(method = "tickMovement", at = @At("HEAD"))
    private void unbeta_pursuit(CallbackInfo ci) {
        MobEntity self = (MobEntity)(Object)this;
        if (self.getWorld().isClient || !(self instanceof CorruptedAnimal)) return;
        float rate = CorruptedAnimal.pursuitAccel(self);
        if (rate > 0.0f) PursuitSpeed.tick(self, rate);
    }

    @Inject(method = "tryAttack", at = @At("RETURN"))
    private void unbeta_resetOnHit(Entity target, CallbackInfoReturnable<Boolean> cir) {
        MobEntity self = (MobEntity)(Object)this;
        if (self instanceof CorruptedAnimal && cir.getReturnValue()) PursuitSpeed.reset(self);
    }
}
