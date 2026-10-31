package net.unbeta.content.mixin;

import net.minecraft.world.ChunkRegion;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.unbeta.content.island.Island;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Island mode: no caves or ravines out at sea (Moderner Beta carves them itself). */
@Pseudo
@Mixin(targets = "mod.bluestaggo.modernerbeta.world.chunk.ModernBetaChunkGenerator", remap = false)
public abstract class IslandCarveMixin {

    // method_12108 is ChunkGenerator.carve (Moderner Beta overrides it).
    @Inject(method = "method_12108", at = @At("HEAD"), cancellable = true, remap = false)
    private void unbeta_noSeaCaves(ChunkRegion region, long seed, NoiseConfig noise, BiomeAccess biomes, StructureAccessor structures,
                                   Chunk chunk, GenerationStep.Carver step, CallbackInfo ci) {
        if (Island.deadChunk(chunk.getPos())) ci.cancel();
    }
}
