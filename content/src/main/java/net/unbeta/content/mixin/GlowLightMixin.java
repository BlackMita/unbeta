package net.unbeta.content.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.world.World;
import net.minecraft.world.chunk.light.ChunkBlockLightProvider;
import net.unbeta.content.glow.Glow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A block rubbed with glowstone dust is a light source wherever it is. */
@Mixin(ChunkBlockLightProvider.class)
public abstract class GlowLightMixin {

    @Inject(method = "getLightSourceLuminance", at = @At("RETURN"), cancellable = true)
    private void unbeta_glow(long blockPos, BlockState state, CallbackInfoReturnable<Integer> cir) {
        if (cir.getReturnValueI() >= Glow.LIGHT) return;
        if (((ChunkLightProviderAccessor) (Object) this).unbeta_chunkProvider().getWorld() instanceof World world
                && Glow.glows(world, blockPos)) {
            cir.setReturnValue(Glow.LIGHT);
        }
    }
}
