package net.unbeta.content.mixin;

import net.minecraft.entity.vehicle.FurnaceMinecartEntity;
import net.unbeta.content.train.TrainCart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A furnace minecart keeps its own (slow) top speed, except while a copper-chained cart drags or
 * pushes it faster: then it keeps up, and afterwards coasts back down to its own pace.
 */
@Mixin(FurnaceMinecartEntity.class)
public abstract class FurnaceMinecartSpeedMixin {

    @Inject(method = "getMaxSpeed", at = @At("RETURN"), cancellable = true)
    private void unbeta_draggedFaster(CallbackInfoReturnable<Double> cir) {
        TrainCart t = (TrainCart) (Object) this;
        if (t.unbeta_dragged() > 0) cir.setReturnValue(Math.max(cir.getReturnValueD(), 0.52));
        else if (t.unbeta_coast() > cir.getReturnValueD()) cir.setReturnValue(t.unbeta_coast());
    }
}
