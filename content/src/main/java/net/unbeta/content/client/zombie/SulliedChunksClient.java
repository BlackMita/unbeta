package net.unbeta.content.client.zombie;

import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;

import java.util.Set;

/** Client-side copy of which nearby chunks are sullied, refreshed once a second. */
public final class SulliedChunksClient {

    private static volatile Set<Long> near = Set.of();

    private SulliedChunksClient() {}

    public static void set(Set<Long> chunks) { near = chunks; }
    public static void clear() { near = Set.of(); }

    public static boolean isSullied(int blockX, int blockZ) {
        return near.contains(ChunkPos.toLong(blockX >> 4, blockZ >> 4));
    }

    /**
     * Y of the top GROUND block in a column: the first block down from the top that has
     * collision and isn't leaves or a log, so the ground under a tree counts and the
     * canopy doesn't. Returns Integer.MIN_VALUE for columns topped by water (no spores
     * on water) or where nothing is found. Uses MOTION_BLOCKING, which - unlike the
     * no-leaves variant - is one of the heightmaps actually synced to clients.
     */
    public static int surfaceY(ClientWorld world, int x, int z) {
        int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z) - 1;
        BlockPos.Mutable p = new BlockPos.Mutable(x, y, z);
        for (int i = 0; i < 48 && y > world.getBottomY(); i++, y--) {
            p.setY(y);
            BlockState s = world.getBlockState(p);
            if (!s.getFluidState().isEmpty()) return Integer.MIN_VALUE;
            if (s.isIn(BlockTags.LEAVES) || s.isIn(BlockTags.LOGS)
                    || s.getCollisionShape(world, p).isEmpty()) continue;
            return y;
        }
        return Integer.MIN_VALUE;
    }
}
