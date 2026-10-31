package net.unbeta.content.clambox;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.fluid.Fluids;

public final class ClamboxSpawner {

    private static final int TICK_INTERVAL = 400;
    private static final int CONVERT_CHANCE = 80;
    private static final int RADIUS = 48;
    private static final int MIN_SPACING = 32;
    /** At most this many seagrass may try to become a clambox per pass. */
    private static final int MAX_ATTEMPTS = 8;
    private static int attempts;

    private ClamboxSpawner() {}

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (world.getTime() % TICK_INTERVAL != 0) return;
            attempts = 0;

            // Every loaded chunk within RADIUS of any player, each once (players standing
            // together share chunks), and in each only the ocean floor of every column:
            // seagrass grows on the floor, so there is no need to read whole cubes of blocks.
            int r = (RADIUS >> 4) + 1;
            java.util.Set<Long> chunks = new java.util.HashSet<>();
            for (var player : world.getPlayers()) {
                ChunkPos c = player.getChunkPos();
                for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                    chunks.add(ChunkPos.toLong(c.x + dx, c.z + dz));
                }
            }
            BlockPos.Mutable pos = new BlockPos.Mutable();
            for (long key : chunks) {
                int cx = ChunkPos.getPackedX(key), cz = ChunkPos.getPackedZ(key);
                if (!world.getChunkManager().isChunkLoaded(cx, cz)) continue;
                net.minecraft.world.chunk.Chunk chunk = world.getChunk(cx, cz);
                for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) {
                    int floor = chunk.sampleHeightmap(Heightmap.Type.OCEAN_FLOOR, lx, lz);
                    pos.set((cx << 4) + lx, floor + 1, (cz << 4) + lz);
                    tryConvert(world, pos.toImmutable());
                }
            }
        });
    }

    private static void tryConvert(ServerWorld world, BlockPos pos) {
        var state = world.getBlockState(pos);
        boolean isSeagrass = state.isOf(Blocks.SEAGRASS);
        boolean isTallBase = state.isOf(Blocks.TALL_SEAGRASS)
                && state.get(net.minecraft.block.TallSeagrassBlock.HALF)
                   == net.minecraft.block.enums.DoubleBlockHalf.LOWER;
        if (!isSeagrass && !isTallBase) return;
        if (world.getRandom().nextInt(CONVERT_CHANCE) != 0) return;
        // Island mode: out in the open sea, half as many.
        if (net.unbeta.content.island.Island.atSea(world, pos) && world.getRandom().nextBoolean()) return;
        if (++attempts > MAX_ATTEMPTS) return;

        // Hard stop: don't convert if a clambox already exists within MIN_SPACING
        if (clamboxNearby(world, pos, MIN_SPACING)) return;

        if (isTallBase) {
            world.setBlockState(pos.up(), Blocks.WATER.getDefaultState());
        }
        // One in four is a wet sponge instead (smelt it dry).
        if (world.getRandom().nextInt(4) == 0) {
            world.setBlockState(pos, Blocks.WET_SPONGE.getDefaultState());
            return;
        }
        world.setBlockState(pos, ClamboxRegistry.CLAMBOX_BLOCK.getDefaultState());

        if (world.getBlockEntity(pos) instanceof ClamboxBlockEntity be) {
            if (world.getRandom().nextBoolean()) {
                be.setStack(ClamboxBlockEntity.SLOT_OUTPUT,
                        ClamboxLoot.roll(world.getRandom()));
            }
            be.markDirty();
        }
    }

    /**
     * A clambox within the spacing already? Looks only at the block entities of the nearby chunks
     * (a clambox has one), not at every block: this used to read some 72,000 blocks per candidate,
     * and near an ocean dozens of candidates come up every pass.
     */
    private static boolean clamboxNearby(ServerWorld world, BlockPos origin, int radius) {
        int r = (radius >> 4) + 1;
        ChunkPos c = new ChunkPos(origin);
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            net.minecraft.world.chunk.WorldChunk chunk = world.getChunkManager().getWorldChunk(c.x + dx, c.z + dz);
            if (chunk == null) continue;
            for (var be : chunk.getBlockEntities().values()) {
                if (!(be instanceof ClamboxBlockEntity)) continue;
                BlockPos p = be.getPos();
                if (Math.abs(p.getX() - origin.getX()) <= radius && Math.abs(p.getZ() - origin.getZ()) <= radius
                        && Math.abs(p.getY() - origin.getY()) <= 8) return true;
            }
        }
        return false;
    }
}
