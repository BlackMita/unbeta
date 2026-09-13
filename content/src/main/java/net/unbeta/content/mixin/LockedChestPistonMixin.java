package net.unbeta.content.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.PistonBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A locked chest is immovable by pistons. */
@Mixin(PistonBlock.class)
public class LockedChestPistonMixin {

    @Inject(method = "isMovable", at = @At("HEAD"), cancellable = true)
    private static void unbeta_lockedChestImmovable(BlockState state, World world, BlockPos pos,
                                                    Direction direction, boolean canBreak,
                                                    Direction pistonDir,
                                                    CallbackInfoReturnable<Boolean> cir) {
        if (world.isClient) return;
        if (!(state.getBlock() instanceof net.minecraft.block.ChestBlock)) return;
        if (net.unbeta.content.lockey.LockeyState.isLocked(
                (net.minecraft.server.world.ServerWorld) world, pos)) {
            cir.setReturnValue(false);
        }
    }
}
