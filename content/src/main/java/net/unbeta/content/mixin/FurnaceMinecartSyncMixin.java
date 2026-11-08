package net.unbeta.content.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.FurnaceMinecartEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.unbeta.content.train.Trains;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fuelling one furnace minecart points every furnace minecart chained into its train the same way. */
@Mixin(FurnaceMinecartEntity.class)
public abstract class FurnaceMinecartSyncMixin {

    @Inject(method = "interact", at = @At("RETURN"))
    private void unbeta_pointTheTrain(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        FurnaceMinecartEntity self = (FurnaceMinecartEntity) (Object) this;
        if (self.getWorld() instanceof ServerWorld world && cir.getReturnValue().isAccepted()) Trains.pointFurnaces(world, self);
    }
}
