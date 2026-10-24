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

    /** Harmless skeletons (holding a non-weapon) cannot deal damage; empty-handed ones grab instead (SkeletonGrip). */
    @Inject(method = "tryAttack", at = @At("HEAD"), cancellable = true)
    private void unbeta_harmlessNoAttack(net.minecraft.entity.Entity target,
                                          CallbackInfoReturnable<Boolean> cir) {
        if ((Object)this instanceof net.minecraft.entity.mob.SkeletonEntity skeleton) {
            // Nothing in its hand (bare, or only wearing what it picked up): it grabs, it doesn't punch.
            if (net.unbeta.content.skeleton.SkeletonGrip.grabs(skeleton)) {
                if (target instanceof net.minecraft.entity.LivingEntity living) {
                    net.unbeta.content.skeleton.SkeletonGrip.grab(skeleton, living);
                }
                cir.setReturnValue(false);
                return;
            }
            if (net.unbeta.content.skeleton.BonePileRegistry.HARMLESS_SKELETONS.contains(skeleton.getUuid())
                    || net.unbeta.content.skeleton.BonePileRegistry.isHarmless(skeleton)) {
                cir.setReturnValue(false);
            }
        }
    }
}