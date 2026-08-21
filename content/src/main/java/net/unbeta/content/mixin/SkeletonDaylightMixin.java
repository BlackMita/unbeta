package net.unbeta.content.mixin;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Skeletons never burn in daylight in Unbeta. */
@Mixin(MobEntity.class)
public class SkeletonDaylightMixin {

    @Inject(method = "isAffectedByDaylight", at = @At("HEAD"), cancellable = true)
    private void unbeta_noDaylightBurn(CallbackInfoReturnable<Boolean> cir) {
        if ((Object)this instanceof SkeletonEntity) {
            cir.setReturnValue(false);
        }
    }

    /** Harmless skeletons (holding non-weapon) cannot deal damage. */
    @Inject(method = "tryAttack", at = @At("HEAD"), cancellable = true)
    private void unbeta_harmlessNoAttack(net.minecraft.entity.Entity target,
                                          CallbackInfoReturnable<Boolean> cir) {
        if ((Object)this instanceof net.minecraft.entity.mob.SkeletonEntity skeleton) {
            if (net.unbeta.content.skeleton.BonePileRegistry.HARMLESS_SKELETONS
                    .contains(skeleton.getUuid())) {
                cir.setReturnValue(false);
            }
        }
    }
}