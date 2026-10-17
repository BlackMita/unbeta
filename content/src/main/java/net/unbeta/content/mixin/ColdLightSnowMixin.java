package net.unbeta.content.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.SnowBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LightType;
import net.unbeta.content.light.Heat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Snow layers only melt when the light reaching them comes from something hot (see Heat). */
@Mixin(SnowBlock.class)
public abstract class ColdLightSnowMixin {

    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void unbeta_onlyHeatMelts(BlockState state, ServerWorld world, BlockPos pos, Random random, CallbackInfo ci) {
        if (world.getLightLevel(LightType.BLOCK, pos) > 11 && !Heat.hotLightAbove(world, pos, 11)) ci.cancel();
    }
}
