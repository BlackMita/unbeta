package net.unbeta.content.mixin;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Iron doors take three times as long to mine, with any tool, locked or not. */
@Mixin(AbstractBlock.class)
public abstract class IronDoorHardnessMixin {

    @Inject(method = "calcBlockBreakingDelta", at = @At("RETURN"), cancellable = true)
    private void unbeta_ironDoorTough(BlockState state, PlayerEntity player, BlockView world, BlockPos pos,
                                      CallbackInfoReturnable<Float> cir) {
        if (state.isOf(Blocks.IRON_DOOR)) cir.setReturnValue(cir.getReturnValueF() / 3.0F);
    }
}
