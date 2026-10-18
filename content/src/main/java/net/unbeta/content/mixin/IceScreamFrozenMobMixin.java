package net.unbeta.content.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.unbeta.content.bucket.IceScream;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A mob frozen by Ice Scream thinks nothing and hits nothing until it thaws. */
@Mixin(MobEntity.class)
public abstract class IceScreamFrozenMobMixin {

    @Inject(method = "tickNewAi", at = @At("HEAD"), cancellable = true)
    private void unbeta_frozenNoAi(CallbackInfo ci) {
        if (IceScream.isFrozen((MobEntity) (Object) this)) ci.cancel();
    }

    @Inject(method = "tryAttack", at = @At("HEAD"), cancellable = true)
    private void unbeta_frozenNoAttack(Entity target, CallbackInfoReturnable<Boolean> cir) {
        if (IceScream.isFrozen((MobEntity) (Object) this)) cir.setReturnValue(false);
    }
}
