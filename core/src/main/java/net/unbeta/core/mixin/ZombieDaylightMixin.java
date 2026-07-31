package net.unbeta.core.mixin;

import net.minecraft.entity.mob.ZombieEntity;
import net.unbeta.core.api.UnbetaApi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Unbeta 1.7.3: "No hostile mobs will catch fire in sunlight."
 *
 * <p>Targets ZombieEntity#burnsInDaylight() - the purpose-built method vanilla checks
 * (verified in 1.20.1 mapped source: tickMovement does
 * {@code burnsInDaylight() && isAffectedByDaylight()} then setOnFireFor(8)).
 * Forcing it false cleanly disables daylight burning without touching the shared
 * isAffectedByDaylight(), which other logic may rely on.
 *
 * <p>Inert unless the mob_daylight_burn system rule is set - so Phase 1 is unaffected
 * and Phase 2 activates it via one line in ContentRules.
 */
@Mixin(ZombieEntity.class)
public abstract class ZombieDaylightMixin {

    @Inject(method = "burnsInDaylight", at = @At("RETURN"), cancellable = true)
    private void unbeta$noDaylightBurn(CallbackInfoReturnable<Boolean> cir) {
        if (!Boolean.TRUE.equals(cir.getReturnValue())) return;
        if (!UnbetaApi.isReady()) return;
        if (UnbetaApi.rules().isSystemDisabled("mob_daylight_burn")) {
            cir.setReturnValue(false);
        }
    }
}
