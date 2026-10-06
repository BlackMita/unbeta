package net.unbeta.content.mixin;

import net.minecraft.structure.StructureStart;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.unbeta.content.stronghold.StrongholdVault;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Remembers which structure is placing its pieces on this thread, so pieces can see the whole layout. */
@Mixin(StructureStart.class)
public abstract class StructureStartPlaceMixin {

    @Inject(method = "place", at = @At("HEAD"))
    private void unbeta_begin(StructureWorldAccess world, StructureAccessor accessor, ChunkGenerator generator,
                              Random random, BlockBox box, ChunkPos chunkPos, CallbackInfo ci) {
        StrongholdVault.CURRENT.set((StructureStart)(Object)this);
    }

    @Inject(method = "place", at = @At("RETURN"))
    private void unbeta_end(StructureWorldAccess world, StructureAccessor accessor, ChunkGenerator generator,
                            Random random, BlockBox box, ChunkPos chunkPos, CallbackInfo ci) {
        StrongholdVault.CURRENT.remove();
    }
}
