package net.unbeta.content.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.IceBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LightType;
import net.unbeta.content.light.Heat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ice only melts when the light reaching it comes from something hot (see Heat). */
@Mixin(IceBlock.class)
public abstract class ColdLightIceMixin {

    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void unbeta_onlyHeatMelts(BlockState state, ServerWorld world, BlockPos pos, Random random, CallbackInfo ci) {
        int threshold = 11 - state.getOpacity(world, pos);
        if (world.getLightLevel(LightType.BLOCK, pos) > threshold && !Heat.hotLightAbove(world, pos, threshold)) ci.cancel();
    }
}
