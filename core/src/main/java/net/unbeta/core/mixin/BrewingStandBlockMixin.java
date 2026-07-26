package net.unbeta.core.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.BrewingStandBlock;
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
 * Brewing is 1.0.0. Beta 1.8 has exactly three reachable status effects, and none of
 * them come from a bottle: Regeneration (golden apple), Poison (cave spider), and
 * Hunger (raw chicken / rotten flesh).
 */
@Mixin(BrewingStandBlock.class)
public abstract class BrewingStandBlockMixin {

    @Inject(method = "onUse", at = @At("HEAD"), cancellable = true)
    private void unbeta$noBrewing(BlockState state, World world, BlockPos pos,
                                  PlayerEntity player, Hand hand, BlockHitResult hit,
                                  CallbackInfoReturnable<ActionResult> cir) {
        if (!UnbetaApi.isReady()) return;
        if (UnbetaApi.rules().isSystemDisabled("brewing")) {
            cir.setReturnValue(ActionResult.FAIL);
        }
    }
}
