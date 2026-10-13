package net.unbeta.content.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LadderBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import net.unbeta.content.ladder.LadderStacking;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ladder columns (see LadderStacking): a ladder stands if its column is anchored by a wall
 * or by the ground. When the ladder above or below changes, re-check - so pulling out the
 * last anchor brings the column down one rung at a time (each drops as an item).
 */
@Mixin(LadderBlock.class)
public abstract class LadderSupportMixin {

    @Inject(method = "canPlaceAt", at = @At("RETURN"), cancellable = true)
    private void unbeta_columnSupport(BlockState state, WorldView world, BlockPos pos,
                                      CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && LadderStacking.anchored(world, pos, state.get(LadderBlock.FACING))) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getStateForNeighborUpdate", at = @At("HEAD"), cancellable = true)
    private void unbeta_columnFalls(BlockState state, Direction direction, BlockState neighborState,
                                    WorldAccess world, BlockPos pos, BlockPos neighborPos,
                                    CallbackInfoReturnable<BlockState> cir) {
        if (direction.getAxis() == Direction.Axis.Y && !state.canPlaceAt(world, pos)) {
            cir.setReturnValue(Blocks.AIR.getDefaultState());
        }
    }
}
