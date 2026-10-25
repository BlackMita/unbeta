package net.unbeta.content.mixin;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Diamond ore takes 50% longer to mine than other ores (hardness 3.0 → 4.5); iron doors are slow without a diamond pickaxe. */
@Mixin(net.minecraft.block.AbstractBlock.class)
public class DiamondOreMiningMixin {

    @Inject(method = "calcBlockBreakingDelta",
            at = @At("RETURN"), cancellable = true)
    private void unbeta_slowDiamondOre(BlockState state, PlayerEntity player,
                                        BlockView world, BlockPos pos,
                                        CallbackInfoReturnable<Float> cir) {
        if (state.isOf(Blocks.DIAMOND_ORE) || state.isOf(Blocks.DEEPSLATE_DIAMOND_ORE)) {
            // Reduce breaking speed by 1/3 (equivalent to 50% more time)
            cir.setReturnValue(cir.getReturnValue() * 0.5F);
        }
        // Iron doors: only a diamond pickaxe makes short work of them. Anything else that can
        // break one (an iron or gold pickaxe) is held to bare-hand speed with the right tool -
        // about 7.5 seconds, well over a wooden axe on a wooden door (~2 s).
        if (state.isOf(Blocks.IRON_DOOR)) {
            net.minecraft.item.Item tool = player.getMainHandStack().getItem();
            if (tool != net.minecraft.item.Items.DIAMOND_PICKAXE && tool != net.minecraft.item.Items.NETHERITE_PICKAXE) {
                float slow = 1.0F / state.getHardness(world, pos) / 30.0F;
                if (cir.getReturnValue() > slow) cir.setReturnValue(slow);
            }
        }
    }
}
