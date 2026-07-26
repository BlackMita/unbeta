package net.unbeta.core.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.unbeta.core.api.UnbetaApi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vines exist in Beta 1.8 but cannot be climbed - that came in 1.0.
 *
 * <p>Injects at RETURN and only downgrades a true to false when the block underfoot
 * is specifically a vine. Ladders, scaffolding and any modded climbable are untouched.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityClimbMixin {

    @Inject(method = "isClimbing", at = @At("RETURN"), cancellable = true)
    private void unbeta$noVineClimbing(CallbackInfoReturnable<Boolean> cir) {
        if (!Boolean.TRUE.equals(cir.getReturnValue())) return;
        if (!UnbetaApi.isReady()) return;
        if (!UnbetaApi.rules().isSystemDisabled("climbable_vines")) return;

        LivingEntity self = (LivingEntity) (Object) this;
        BlockState state = self.getWorld().getBlockState(self.getBlockPos());
        if (state.isOf(Blocks.VINE)) {
            cir.setReturnValue(false);
        }
    }
}
