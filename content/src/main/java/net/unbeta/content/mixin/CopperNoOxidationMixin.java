package net.unbeta.content.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.OxidizableBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Copper stays copper-coloured forever. In vanilla a copper block randomly ages into the
 * exposed, weathered and oxidized blocks - which are removed in Unbeta - so aging is
 * switched off entirely. (Honeycomb is removed too, so waxing never comes up.)
 */
@Mixin(OxidizableBlock.class)
public abstract class CopperNoOxidationMixin {

    @Inject(method = "hasRandomTicks", at = @At("HEAD"), cancellable = true)
    private void unbeta_noAging(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }

    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void unbeta_noAgingTick(BlockState state, ServerWorld world, BlockPos pos, Random random,
                                    CallbackInfo ci) {
        ci.cancel();
    }
}
