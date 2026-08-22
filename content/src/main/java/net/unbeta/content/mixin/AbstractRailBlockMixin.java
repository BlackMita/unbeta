package net.unbeta.content.mixin;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FenceBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fence tops count as valid rail support — patches the real chokepoint on Block. */
@Mixin(Block.class)
public class AbstractRailBlockMixin {

    @Inject(method = "hasTopRim", at = @At("RETURN"), cancellable = true)
    private static void unbeta_fenceTopIsRailSupport(BlockView world, BlockPos pos,
                                                     CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) {
            if (world.getBlockState(pos).getBlock() instanceof FenceBlock) {
                cir.setReturnValue(true);
            }
        }
    }
}
