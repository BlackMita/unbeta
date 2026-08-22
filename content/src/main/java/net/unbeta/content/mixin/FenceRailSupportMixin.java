package net.unbeta.content.mixin;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Trims the fence collision box to 1.0 blocks when a rail is directly above,
 * so minecarts can travel over fence-supported rails without being stopped.
 */
@Mixin(AbstractBlock.class)
public class FenceRailSupportMixin {

    @Inject(method = "getCollisionShape", at = @At("RETURN"), cancellable = true)
    private void unbeta_trimFenceUnderRail(BlockState state, BlockView world, BlockPos pos,
                                            ShapeContext ctx,
                                            CallbackInfoReturnable<VoxelShape> cir) {
        if (!(state.getBlock() instanceof FenceBlock)) return;
        if (!(world.getBlockState(pos.up()).getBlock() instanceof AbstractRailBlock)) return;
        // Replace the fence collision shape with just the 1-block-tall post core.
        // This removes the 0.5-block protrusion that stops minecarts and players.
        cir.setReturnValue(net.minecraft.block.Block.createCuboidShape(6, 0, 6, 10, 16, 10));
    }
}
