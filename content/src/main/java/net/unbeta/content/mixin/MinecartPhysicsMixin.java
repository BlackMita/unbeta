package net.unbeta.content.mixin;

import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.Constant;

/**
 * Makes powered rails noticeably better:
 * - 2x top speed cap (0.4 -> 0.8 blocks/tick on land)
 * - 2x powered rail acceleration (0.0078125 -> 0.015625)
 * - Much less friction (0.96 -> 0.998 empty, 0.997 -> 0.9995 with passengers)
 */
@Mixin(AbstractMinecartEntity.class)
public class MinecartPhysicsMixin {

    /** Double the speed cap. */
    @Inject(method = "getMaxSpeed", at = @At("RETURN"), cancellable = true)
    private void unbeta_doubleMaxSpeed(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(cir.getReturnValue() * 2.0);
    }

    /** Double the powered rail acceleration constant. */
    @ModifyConstant(method = "moveOnRail",
            constant = @Constant(doubleValue = 0.0078125))
    private double unbeta_doubleAcceleration(double original) {
        return original * 2.0;
    }

    /** Much less friction - boost lasts far longer. */
    @ModifyConstant(method = "applySlowdown",
            constant = @Constant(doubleValue = 0.96))
    private double unbeta_lessEmptyFriction(double original) {
        return 0.998;
    }

    @ModifyConstant(method = "applySlowdown",
            constant = @Constant(doubleValue = 0.997))
    private double unbeta_lessLoadedFriction(double original) {
        return 0.9995;
    }
}
