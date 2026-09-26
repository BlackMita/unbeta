package net.unbeta.content.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.MyceliumBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mycelium no longer puffs spores. That particle now belongs to sullied chunks instead
 * (see SulliedChunkParticles), where it marks ground that remembers the dead.
 *
 * <p>Client-only: randomDisplayTick is the client's ambient-effects hook.
 */
@Mixin(MyceliumBlock.class)
public abstract class MyceliumNoParticlesMixin {

    @Inject(method = "randomDisplayTick", at = @At("HEAD"), cancellable = true)
    private void unbeta_noSpores(BlockState state, World world, BlockPos pos, Random random,
                                 CallbackInfo ci) {
        ci.cancel();
    }
}
