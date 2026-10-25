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

/** Iron doors take three times as long to mine, locked or not, and at least 7.5 s without a diamond pickaxe. */
@Mixin(AbstractBlock.class)
public abstract class IronDoorHardnessMixin {

    @Inject(method = "calcBlockBreakingDelta", at = @At("RETURN"), cancellable = true)
    private void unbeta_ironDoorTough(BlockState state, PlayerEntity player, BlockView world, BlockPos pos,
                                      CallbackInfoReturnable<Float> cir) {
        if (!state.isOf(Blocks.IRON_DOOR)) return;
        float delta = cir.getReturnValueF() / 3.0F;
        // Only a diamond pickaxe makes reasonable work of one (about 2.8 s). Anything else that
        // can break it (an iron or gold pickaxe) takes at least 7.5 s - longer than a wooden axe
        // on a wooden door.
        net.minecraft.item.Item tool = player.getMainHandStack().getItem();
        if (tool != net.minecraft.item.Items.DIAMOND_PICKAXE && tool != net.minecraft.item.Items.NETHERITE_PICKAXE) {
            delta = Math.min(delta, 1.0F / state.getHardness(world, pos) / 30.0F);
        }
        cir.setReturnValue(delta);
    }
}
