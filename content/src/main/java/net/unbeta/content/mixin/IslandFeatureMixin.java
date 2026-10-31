package net.unbeta.content.mixin;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.World;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.PlacedFeature;
import net.unbeta.content.island.Island;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Island mode: out at sea, nothing generates but seagrass and kelp (no ores, no anything). */
@Mixin(PlacedFeature.class)
public abstract class IslandFeatureMixin {

    @Inject(method = {"generate(Lnet/minecraft/world/StructureWorldAccess;Lnet/minecraft/world/gen/chunk/ChunkGenerator;Lnet/minecraft/util/math/random/Random;Lnet/minecraft/util/math/BlockPos;)Z",
            "generateUnregistered"}, at = @At("HEAD"), cancellable = true)
    private void unbeta_emptySea(StructureWorldAccess world, ChunkGenerator generator, Random random, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!Island.active() || !Island.deadChunk(new ChunkPos(pos))) return;
        if (world.toServerWorld().getRegistryKey() != World.OVERWORLD) return;
        Feature<?> f = ((PlacedFeature) (Object) this).feature().value().feature();
        if (f instanceof net.minecraft.world.gen.feature.SeagrassFeature || f instanceof net.minecraft.world.gen.feature.KelpFeature) return;
        cir.setReturnValue(false);
    }
}
