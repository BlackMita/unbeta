package net.unbeta.content.mixin;

import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tuned minecart physics:
 * - 1.3x top speed (0.4 -> 0.52) - noticeable but won't overshoot turns
 * - Passenger multiplier 0.75 -> 0.95 - fixes turn momentum loss with rider
 * - Uphill drag halved (0.0078125 -> 0.00390625) - ascents feel less punishing
 * - Powered rail boost force 0.06 -> 0.10 - meaningful without flying off track
 * - Friction 0.96 -> 0.985 (empty), 0.997 -> 0.999 (loaded) - momentum preserved longer
 */
@Mixin(AbstractMinecartEntity.class)
public class MinecartPhysicsMixin {

    /** 1.3x top speed cap. */
    @Inject(method = "getMaxSpeed", at = @At("RETURN"), cancellable = true)
    private void unbeta_tuneMaxSpeed(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(cir.getReturnValue() * 1.3);
    }

    /** Halve uphill drag so ascents feel less punishing. */
    @ModifyConstant(method = "moveOnRail",
            constant = @Constant(doubleValue = 0.0078125))
    private double unbeta_reduceUphillDrag(double original) {
        return original * 0.5;
    }

    /** Passenger speed multiplier: 0.75 -> 0.95 fixes turn momentum loss with rider. */
    @ModifyConstant(method = "moveOnRail",
            constant = @Constant(doubleValue = 0.75))
    private double unbeta_improvePassengerMultiplier(double original) {
        return 0.95;
    }

    /** Powered rail boost force: 0.06 -> 0.10. */
    @ModifyConstant(method = "moveOnRail",
            constant = @Constant(doubleValue = 0.06))
    private double unbeta_improveBoostForce(double original) {
        return 0.10;
    }

    /** Less friction when empty: 0.96 -> 0.985. */
    @ModifyConstant(method = "applySlowdown",
            constant = @Constant(doubleValue = 0.96))
    private double unbeta_lessEmptyFriction(double original) {
        return 0.985;
    }

    /** Less friction when loaded: 0.997 -> 0.999. */
    @ModifyConstant(method = "applySlowdown",
            constant = @Constant(doubleValue = 0.997))
    private double unbeta_lessLoadedFriction(double original) {
        return 0.999;
    }
}
