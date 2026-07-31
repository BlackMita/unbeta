package net.unbeta.core.mixin;

import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.MobEntity;
import net.unbeta.core.api.UnbetaApi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Handles daylight burning for mobs that INLINE the check rather than exposing a
 * burnsInDaylight() method - specifically AbstractSkeletonEntity, whose tickMovement
 * does {@code boolean bl = this.isAffectedByDaylight();} then setOnFireFor(8)
 * (verified in 1.20.1 mapped source).
 *
 * <p>Zombies are handled separately by {@link ZombieDaylightMixin}, which targets their
 * dedicated burnsInDaylight() - cleaner and already proven working.
 *
 * <p><b>Scope:</b> isAffectedByDaylight() lives on MobEntity and is shared, so this
 * override is guarded by {@code instanceof AbstractSkeletonEntity}. It only ever returns
 * false for skeletons when the rule is active; every other mob's result is untouched.
 * To cover additional daylight-burners later, widen the instanceof check.
 */
@Mixin(MobEntity.class)
public abstract class MobDaylightMixin {

    @Inject(method = "isAffectedByDaylight", at = @At("RETURN"), cancellable = true)
    private void unbeta$noSkeletonDaylightBurn(CallbackInfoReturnable<Boolean> cir) {
        if (!Boolean.TRUE.equals(cir.getReturnValue())) return;
        if (!UnbetaApi.isReady()) return;
        if (!UnbetaApi.rules().isSystemDisabled("mob_daylight_burn")) return;

        if ((Object) this instanceof AbstractSkeletonEntity) {
            cir.setReturnValue(false);
        }
    }
}
