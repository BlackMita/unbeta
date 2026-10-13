package net.unbeta.content.mixin;

import net.minecraft.block.TntBlock;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.unbeta.content.lockey.LockeyDoors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** TNT inside a locked door's zone won't prime (it couldn't be removed, so it would multiply). */
@Mixin(TntBlock.class)
public abstract class LockedDoorTntMixin {

    @Inject(method = "primeTnt(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/entity/LivingEntity;)V",
            at = @At("HEAD"), cancellable = true)
    private static void unbeta_noPrimeInZone(World world, BlockPos pos, LivingEntity igniter, CallbackInfo ci) {
        if (world instanceof ServerWorld sw && LockeyDoors.isProtected(sw, pos)) ci.cancel();
    }
}
