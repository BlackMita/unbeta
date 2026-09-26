package net.unbeta.content.mixin;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.WaterFluid;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.unbeta.content.torch.UnbetaTorchBlock;
import net.unbeta.content.torch.UnbetaTorchRegistry;
import net.unbeta.content.torch.UnbetaWallTorchBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Water washing away a LIT Unbeta torch puts it out: it drops as an unlit torch, with a
 * hiss, instead of the lit item its normal loot would give. Hooked on the moment water
 * destroys a block, so a torch mined by hand next to water still drops lit.
 */
@Mixin(WaterFluid.class)
public abstract class TorchWaterMixin {

    @Inject(method = "beforeBreakingBlock", at = @At("HEAD"), cancellable = true)
    private void unbeta_doused(WorldAccess world, BlockPos pos, BlockState state, CallbackInfo ci) {
        boolean torch = state.getBlock() instanceof UnbetaTorchBlock
                || state.getBlock() instanceof UnbetaWallTorchBlock;
        if (!torch || !state.contains(Properties.LIT) || !state.get(Properties.LIT)) return;
        if (!(world instanceof World w) || w.isClient) return;
        Block.dropStack(w, pos, new ItemStack(UnbetaTorchRegistry.TORCH_ITEM)); // unlit
        w.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.5F, 1.4F);
        ci.cancel();
    }
}
