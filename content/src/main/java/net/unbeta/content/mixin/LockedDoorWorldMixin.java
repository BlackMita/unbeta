package net.unbeta.content.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.unbeta.content.lockey.LockeyDoors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every block change in a world funnels through here, so this is where a locked door's
 * zone is made indestructible - fire, Burnt, fluids, mobs, other mods - and where a locked
 * door is kept shut against redstone and anything else that tries to swing it.
 */
@Mixin(World.class)
public abstract class LockedDoorWorldMixin {

    @Inject(method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z",
            at = @At("HEAD"), cancellable = true)
    private void unbeta_lockedDoorZone(BlockPos pos, BlockState state, int flags, int maxUpdateDepth,
                                       CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerWorld world && LockeyDoors.blocksChange(world, pos, state)) {
            cir.setReturnValue(false);
        }
    }
}
