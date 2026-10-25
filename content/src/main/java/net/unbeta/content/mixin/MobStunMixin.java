package net.unbeta.content.mixin;

import net.minecraft.entity.mob.MobEntity;
import net.unbeta.content.taser.Taser;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A tased mob thinks nothing: no goals, no pathing, no attacks while the current holds it. */
@Mixin(MobEntity.class)
public abstract class MobStunMixin {

    @Inject(method = "tickNewAi", at = @At("HEAD"), cancellable = true)
    private void unbeta_stunned(CallbackInfo ci) {
        if (Taser.isStunned((MobEntity) (Object) this)) ci.cancel();
    }
}
