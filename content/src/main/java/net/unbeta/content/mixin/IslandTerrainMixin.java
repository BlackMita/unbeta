package net.unbeta.content.mixin;

import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.unbeta.content.island.Island;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

/**
 * Island mode, inside Moderner Beta's terrain maker: its own island shaping, driven by the
 * island size, and the deep sea floor flattened once a chunk's terrain is made.
 */
@Pseudo
@Mixin(targets = "mod.bluestaggo.modernerbeta.api.world.chunk.ChunkProviderNoise", remap = false)
public abstract class IslandTerrainMixin {

    @Shadow(remap = false) @Final protected int noiseSizeX;

    @Inject(method = "getIslandOffset", at = @At("HEAD"), cancellable = true, remap = false)
    private void unbeta_island(int noiseX, int noiseZ, CallbackInfoReturnable<Double> cir) {
        if (!Island.active()) return;
        double perCell = 16.0 / Math.max(1, noiseSizeX);
        cir.setReturnValue(Island.densityOffset(noiseX * perCell, noiseZ * perCell));
    }

    @Inject(method = "provideChunk", at = @At("RETURN"), cancellable = true, remap = false)
    private void unbeta_seaFloor(Blender blender, StructureAccessor structures, Chunk chunk, NoiseConfig noise,
                                 CallbackInfoReturnable<CompletableFuture<Chunk>> cir) {
        if (!Island.active() || cir.getReturnValue() == null) return;
        cir.setReturnValue(cir.getReturnValue().thenApply(c -> {
            Island.flattenSeaFloor(c);
            return c;
        }));
    }
}
