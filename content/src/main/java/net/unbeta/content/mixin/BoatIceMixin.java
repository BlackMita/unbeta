package net.unbeta.content.mixin;

import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A boat on ice loses nothing to it while you paddle forward, so it goes faster and faster (up to
 * 80 blocks a second); let go and it coasts a long way. Turning slows as on ordinary ice.
 */
@Mixin(BoatEntity.class)
public abstract class BoatIceMixin {

    @Shadow private boolean pressingForward;
    @Shadow private float yawVelocity;

    @Unique private static final double LIMIT = 4.0;
    @Unique private boolean unbeta_onIce;

    @Inject(method = "getNearbySlipperiness", at = @At("RETURN"), cancellable = true)
    private void unbeta_iceGlide(CallbackInfoReturnable<Float> cir) {
        unbeta_onIce = cir.getReturnValueF() >= 0.97F;
        if (unbeta_onIce) cir.setReturnValue(pressingForward ? 1.0F : 0.995F);
    }

    @Inject(method = "updateVelocity", at = @At("TAIL"))
    private void unbeta_iceLimit(CallbackInfo ci) {
        if (!unbeta_onIce) return;
        BoatEntity self = (BoatEntity) (Object) this;
        yawVelocity *= 0.98F;
        Vec3d v = self.getVelocity();
        double h = v.horizontalLength();
        if (h > LIMIT) self.setVelocity(v.x * LIMIT / h, v.y, v.z * LIMIT / h);
    }
}
