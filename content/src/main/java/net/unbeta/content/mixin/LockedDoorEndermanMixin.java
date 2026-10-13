package net.unbeta.content.mixin;

import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.server.world.ServerWorld;
import net.unbeta.content.lockey.LockeyDoors;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Endermen don't take blocks from a locked door's zone (they reach about 2-3 blocks). */
@Mixin(targets = "net.minecraft.entity.mob.EndermanEntity$PickUpBlockGoal")
public abstract class LockedDoorEndermanMixin {

    @Shadow @Final private EndermanEntity enderman;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void unbeta_handsOff(CallbackInfo ci) {
        if (enderman.getWorld() instanceof ServerWorld sw && LockeyDoors.isNear(sw, enderman.getBlockPos(), 4)) ci.cancel();
    }
}
