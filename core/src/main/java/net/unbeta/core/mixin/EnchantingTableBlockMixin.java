package net.unbeta.core.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.EnchantingTableBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.unbeta.core.api.UnbetaApi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Enchanting is 1.0.0. The table is already unobtainable (gated item, no recipe, no
 * creative entry) - this closes the last door: a table placed by /setblock, or one
 * surviving in an imported world, will not open its screen.
 */
@Mixin(EnchantingTableBlock.class)
public abstract class EnchantingTableBlockMixin {

    @Inject(method = "onUse", at = @At("HEAD"), cancellable = true)
    private void unbeta$noEnchanting(BlockState state, World world, BlockPos pos,
                                     PlayerEntity player, Hand hand, BlockHitResult hit,
                                     CallbackInfoReturnable<ActionResult> cir) {
        if (!UnbetaApi.isReady()) return;
        if (UnbetaApi.rules().isSystemDisabled("enchanting")) {
            cir.setReturnValue(ActionResult.FAIL);
        }
    }
}
