package net.unbeta.content.mixin;

import net.minecraft.entity.vehicle.FurnaceMinecartEntity;
import net.unbeta.content.train.TrainCart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A furnace minecart keeps its own (slow) top speed, except while a copper chain drags it along
 * behind a faster cart: then it can keep up with the cart pulling it.
 */
@Mixin(FurnaceMinecartEntity.class)
public abstract class FurnaceMinecartSpeedMixin {

    @Inject(method = "getMaxSpeed", at = @At("RETURN"), cancellable = true)
    private void unbeta_draggedFaster(CallbackInfoReturnable<Double> cir) {
        if (((TrainCart) (Object) this).unbeta_dragged() > 0) cir.setReturnValue(Math.max(cir.getReturnValueD(), 0.52));
    }
}
