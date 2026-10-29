package net.unbeta.content.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import net.unbeta.content.glow.Glow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A glowing block that's gone (mined, blown up, burnt, washed away) takes its glow with it. */
@Mixin(WorldChunk.class)
public abstract class GlowBlockChangeMixin {

    @Shadow public abstract World getWorld();

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void unbeta_glowGone(BlockPos pos, BlockState state, boolean moved, CallbackInfoReturnable<BlockState> cir) {
        if (cir.getReturnValue() == null || !(getWorld() instanceof ServerWorld sw) || !Glow.anyServer(sw)) return;
        if (Glow.gone(state) && Glow.glows(sw, pos.asLong())) Glow.remove(sw, pos);
    }
}
