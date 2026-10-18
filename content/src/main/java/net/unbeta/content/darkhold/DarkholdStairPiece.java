package net.unbeta.content.darkhold;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.structure.StructureContext;
import net.minecraft.structure.StructurePiece;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;

/**
 * A stairwell: one cell, two floors tall, no stairs or slabs in it - just 2-block-high obsidian
 * ledges spiralling round a central pillar up to a ledge that rings the upper floor. Nobody
 * can jump 2 blocks: climbing it takes a block to stand on, a ladder or an Ender Pearl.
 *
 * <p>Cell-local layout (interior 1..9): ledges at heights 2, 4, 6, 8 (the pillar) and 10 going round;
 * the outer 1-block band is kept clear on the lower floor so every doorway can reach the
 * first ledge, and on the upper floor it is the ring every upper doorway opens onto.
 */
public final class DarkholdStairPiece extends StructurePiece {

    /** {minX, maxX, minZ, maxZ, top} for each ledge, cell-local, bottom to top. */
    private static final int[][] LEDGES = {
            {3, 6, 2, 3, 2},
            {7, 8, 3, 6, 4},
            {4, 7, 7, 8, 6},
            {4, 6, 4, 6, 8},     // the central pillar
            {2, 3, 4, 7, 10},    // level with the upper floor's ring
    };

    private final int x0, y0, z0;

    public DarkholdStairPiece(int x0, int y0, int z0) {
        super(Darkhold.STAIR, 0, new BlockBox(x0, y0, z0, x0 + 10, y0 + 20, z0 + 10));
        this.x0 = x0;
        this.y0 = y0;
        this.z0 = z0;
    }

    public DarkholdStairPiece(NbtCompound nbt) {
        super(Darkhold.STAIR, nbt);
        this.x0 = nbt.getInt("X");
        this.y0 = nbt.getInt("Y");
        this.z0 = nbt.getInt("Z");
    }

    @Override
    protected void writeNbt(StructureContext context, NbtCompound nbt) {
        nbt.putInt("X", x0);
        nbt.putInt("Y", y0);
        nbt.putInt("Z", z0);
    }

    private static boolean ledge(int x, int y, int z) {
        for (int[] l : LEDGES) {
            if (x >= l[0] && x <= l[1] && z >= l[2] && z <= l[3] && y <= l[4]) return true;
        }
        return false;
    }

    @Override
    public void generate(StructureWorldAccess world, StructureAccessor structures, ChunkGenerator generator,
                         Random random, BlockBox box, ChunkPos chunkPos, BlockPos pivot) {
        for (int x = 0; x <= 10; x++) {
            for (int z = 0; z <= 10; z++) {
                boolean edge = x == 0 || x == 10 || z == 0 || z == 10;
                boolean band = x == 1 || x == 9 || z == 1 || z == 9;
                for (int y = 0; y <= 20; y++) {
                    boolean solid = y == 0 || y == 20 || edge
                            || (y == 10 && band)            // the upper floor's ring
                            || (y >= 1 && ledge(x, y, z));
                    DarkholdBlocks.put(world, box, x0 + x, y0 + y, z0 + z,
                            solid ? DarkholdBlocks.OBSIDIAN : DarkholdBlocks.AIR);
                }
            }
        }
    }
}
