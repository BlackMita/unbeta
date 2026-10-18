package net.unbeta.content.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LadderBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Smart Ladders picks up or down from where you click a ladder, and gives up if that end is
 * blocked - so a ladder standing on the ground refused to grow whenever you clicked its lower
 * half. Now a blocked end falls back to the other end: click anywhere on a column to extend it.
 */
@Pseudo
@Mixin(targets = "fabric.org.zerw.net.smartladders.ladder.LadderExtender", remap = false)
public abstract class SmartLadderExtendMixin {

    @Inject(method = "findExtendPos", at = @At("RETURN"), cancellable = true, remap = false)
    private static void unbeta_tryOtherEnd(World world, BlockPos start, Direction facing, Direction extend,
                                           CallbackInfoReturnable<BlockPos> cir) {
        if (cir.getReturnValue() != null) return;
        Direction other = extend.getOpposite();
        BlockPos.Mutable cursor = start.mutableCopy();
        for (int i = 0; i < 384 && sameLadder(world.getBlockState(cursor.offset(other)), facing); i++) cursor.move(other);
        cursor.move(other);
        if (world.isOutOfHeightLimit(cursor) || !world.getWorldBorder().contains(cursor)) return;
        if (!world.getBlockState(cursor).isReplaceable()) return;
        cir.setReturnValue(cursor.toImmutable());
    }

    private static boolean sameLadder(BlockState state, Direction facing) {
        return state.isOf(Blocks.LADDER) && state.get(LadderBlock.FACING) == facing;
    }
}
