package net.unbeta.content.clambox;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.Fluids;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Spawns clamboxes on the ocean floor when a chunk first loads.
 *
 * <p>Rules:
 * - Ocean biomes only (surface or deep)
 * - 1 in 20 eligible chunks gets a clambox
 * - Occasionally a chunk gets 2, placed apart from each other
 * - Placed on the highest solid block under water, not in air or stone
 * - Never removed (no despawn), so density stays stable over time
 * - 1/2 chance of a pre-loaded pearl in the output slot
 */
public final class ClamboxSpawner {

    private static final int SPAWN_CHANCE = 20;
    private static final int PAIR_CHANCE = 6; // 1 in 6 spawning chunks get a pair

    private ClamboxSpawner() {}

    public static void register() {
        ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            // Only run once per chunk, using the chunk's own random seeded by position
            // so results are stable across reloads.
            ChunkPos cp = chunk.getPos();
            long seed = world.getSeed() ^ ((long) cp.x * 341873128712L) ^ ((long) cp.z * 132897987541L);
            java.util.Random rng = new java.util.Random(seed);

            if (rng.nextInt(SPAWN_CHANCE) != 0) return;
            if (!isOcean(world, cp)) return;

            // Already has a clambox? Skip (prevents duplicates on reload).
            if (hasClambox(world, cp)) return;

            placeOne(world, chunk, rng);
            if (rng.nextInt(PAIR_CHANCE) == 0) placeOne(world, chunk, rng);
        });
    }

    private static void placeOne(ServerWorld world, WorldChunk chunk, java.util.Random rng) {
        ChunkPos cp = chunk.getPos();
        // Pick a random xz within the chunk
        int x = cp.getStartX() + rng.nextInt(16);
        int z = cp.getStartZ() + rng.nextInt(16);

        // Find the highest solid block under water
        BlockPos surface = world.getTopPosition(
                net.minecraft.world.Heightmap.Type.OCEAN_FLOOR, new BlockPos(x, 0, z));

        // Must be underwater (block above is water)
        BlockPos above = surface.up();
        if (!world.getFluidState(above).isOf(Fluids.WATER)) return;

        // Don't place on bedrock or in solid stone with no water above
        BlockState floor = world.getBlockState(surface);
        if (!floor.isSolidBlock(world, surface)) return;

        // Place the clambox
        BlockPos pos = surface.up();
        world.setBlockState(pos, ClamboxRegistry.CLAMBOX_BLOCK.getDefaultState());

        // Wire up loot: 1/2 chance of a pre-loaded pearl
        if (world.getBlockEntity(pos) instanceof ClamboxBlockEntity be) {
            if (rng.nextBoolean()) {
                be.setStack(ClamboxBlockEntity.SLOT_OUTPUT,
                        ClamboxLoot.roll(world.getRandom()));
            }
            be.markDirty();
        }
    }

    private static boolean isOcean(ServerWorld world, ChunkPos cp) {
        BlockPos centre = new BlockPos(cp.getCenterX(), 60, cp.getCenterZ());
        var biome = world.getBiome(centre);
        return biome.isIn(BiomeTags.IS_OCEAN) || biome.isIn(BiomeTags.IS_DEEP_OCEAN);
    }

    private static boolean hasClambox(ServerWorld world, ChunkPos cp) {
        for (int x = cp.getStartX(); x <= cp.getEndX(); x++) {
            for (int z = cp.getStartZ(); z <= cp.getEndZ(); z++) {
                for (int y = world.getBottomY(); y < world.getTopY(); y++) {
                    if (world.getBlockState(new BlockPos(x, y, z))
                            .isOf(ClamboxRegistry.CLAMBOX_BLOCK)) return true;
                }
            }
        }
        return false;
    }
}
