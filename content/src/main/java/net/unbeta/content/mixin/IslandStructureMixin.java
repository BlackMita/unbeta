package net.unbeta.content.mixin;

import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.placement.StructurePlacementCalculator;
import net.unbeta.content.island.Island;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Island mode: no structure starts off the island. */
@Mixin(ChunkGenerator.class)
public abstract class IslandStructureMixin {

    @Inject(method = "setStructureStarts", at = @At("HEAD"), cancellable = true)
    private void unbeta_onlyOnIsland(DynamicRegistryManager registries, StructurePlacementCalculator calculator, StructureAccessor structures,
                                     Chunk chunk, StructureTemplateManager templates, CallbackInfo ci) {
        if (calculator == Island.CALCULATOR && Island.offIsland(chunk.getPos())) ci.cancel();
    }
}
