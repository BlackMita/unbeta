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
 * Freestanding ladders: a ladder may stand with nothing behind it as long as there is a ladder
 * or the solid top of a block directly under it. Take the bottom out and the column above it
 * falls apart, rung by rung (each drops as an item), like scaffolding.
 */
@Mixin(LadderBlock.class)
public abstract class LadderSupportMixin {

    @Inject(method = "canPlaceAt", at = @At("RETURN"), cancellable = true)
    private void unbeta_standOnSomething(BlockState state, WorldView world, BlockPos pos,
                                         CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && LadderStacking.supportedFromBelow(world, pos)) cir.setReturnValue(true);
    }

    @Inject(method = "getStateForNeighborUpdate", at = @At("HEAD"), cancellable = true)
    private void unbeta_fallWithoutSupport(BlockState state, Direction direction, BlockState neighborState,
                                           WorldAccess world, BlockPos pos, BlockPos neighborPos,
                                           CallbackInfoReturnable<BlockState> cir) {
        if (direction == Direction.DOWN && !state.canPlaceAt(world, pos)) cir.setReturnValue(Blocks.AIR.getDefaultState());
    }
}
