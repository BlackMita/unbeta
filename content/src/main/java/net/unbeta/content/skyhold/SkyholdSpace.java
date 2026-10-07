package net.unbeta.content.skyhold;

import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.gen.structure.Structure;

/**
 * "Is this inside a Skyhold?" - true within a Skyhold's footprint, between y 190 and the build
 * limit. Reads only chunks that are already loaded (never loads or waits on one), so it is safe
 * to ask from spawning code.
 */
public final class SkyholdSpace {

    private SkyholdSpace() {}

    public static boolean isInside(ServerWorld world, BlockPos pos) {
        if (world.getRegistryKey() != World.OVERWORLD) return false;
        if (pos.getY() < 190) return false;
        Structure skyhold = world.getRegistryManager().get(RegistryKeys.STRUCTURE).get(Skyhold.ID);
        if (skyhold == null) return false;
        WorldChunk chunk = world.getChunkManager().getWorldChunk(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk != null && !chunk.getStructureReferences(skyhold).isEmpty();
    }

    /** Within a Skyhold's footprint at any height (where its fog begins). Loaded chunks only. */
    public static boolean inFootprint(ServerWorld world, BlockPos pos) {
        if (world.getRegistryKey() != World.OVERWORLD) return false;
        Structure skyhold = world.getRegistryManager().get(RegistryKeys.STRUCTURE).get(Skyhold.ID);
        if (skyhold == null) return false;
        WorldChunk chunk = world.getChunkManager().getWorldChunk(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk != null && !chunk.getStructureReferences(skyhold).isEmpty();
    }
}
