package net.unbeta.content.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkRegion;
import net.unbeta.content.torch.UnbetaTorchRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Intercepts all block placements during worldgen and replaces vanilla torches
 * with Unbeta unlit torches. Catches procedural placements (mineshafts, villages,
 * dungeons) since those structures place blocks in Java code, not .nbt templates.
 * Only fires during worldgen - player-placed torches are unaffected.
 */
@Mixin(ChunkRegion.class)
public class ChunkRegionTorchMixin {

    @ModifyVariable(
        method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z",
        at = @At("HEAD"),
        argsOnly = true,
        index = 2
    )
    private BlockState unbeta_replaceTorch(BlockState state) {
        if (state.isOf(Blocks.TORCH)) {
            return UnbetaTorchRegistry.TORCH.getDefaultState();
        }
        if (state.isOf(Blocks.WALL_TORCH)) {
            return UnbetaTorchRegistry.WALL_TORCH
                    .getDefaultState()
                    .with(HorizontalFacingBlock.FACING, state.get(HorizontalFacingBlock.FACING));
        }
        return state;
    }
}
