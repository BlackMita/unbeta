package net.unbeta.content.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LadderBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ladders are the Smart Ladders mod's: wall, ceiling and column support, extending up or down.
 * Unbeta adds one thing on top: a ladder can also stand on the ground. A column of ladders
 * (all facing the same way) whose bottom rung rests on the solid top of a block stays up, so
 * clicking a floor places a ladder facing you. Hooked into Smart Ladders' own support check,
 * so its placement and its pop-off-when-unsupported both respect it.
 */
@Pseudo
@Mixin(targets = "fabric.org.zerw.net.smartladders.ladder.LadderSupport", remap = false)
public abstract class SmartLadderGroundMixin {

    @Inject(method = "canSurvive", at = @At("RETURN"), cancellable = true, remap = false)
    private static void unbeta_standOnGround(BlockState state, WorldView world, BlockPos pos,
                                             CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() || !state.isOf(Blocks.LADDER)) return;
        Direction facing = state.get(LadderBlock.FACING);
        BlockPos p = pos;
        for (int i = 0; i < 384; i++) {
            BlockPos down = p.down();
            BlockState below = world.getBlockState(down);
            if (below.isOf(Blocks.LADDER) && below.get(LadderBlock.FACING) == facing) {
                p = down;
                continue;
            }
            if (below.isSideSolidFullSquare(world, down, Direction.UP)) cir.setReturnValue(true);
            return;
        }
    }
}
