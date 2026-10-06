package net.unbeta.content.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.FluidBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldAccess;
import net.unbeta.content.bucket.TempFluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** No bucket can pick up a temporary source from a wood bucket. */
@Mixin(FluidBlock.class)
public abstract class TempFluidDrainMixin {

    @Inject(method = "tryDrainFluid", at = @At("HEAD"), cancellable = true)
    private void unbeta_noDrainTemp(WorldAccess world, BlockPos pos, BlockState state,
                                    CallbackInfoReturnable<ItemStack> cir) {
        if (TempFluids.isTemporary(world, pos)) cir.setReturnValue(ItemStack.EMPTY);
    }
}
