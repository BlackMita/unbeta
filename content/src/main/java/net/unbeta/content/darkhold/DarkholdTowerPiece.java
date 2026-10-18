package net.unbeta.content.darkhold;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.structure.StructureContext;
import net.minecraft.structure.StructurePiece;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;

/**
 * A watch tower standing apart from the Darkhold: a roundish 7 x 7 obsidian shaft, 1 to 5
 * floors (10 blocks each) tall, hollow all the way up, with a 1 x 2 doorway at the bottom
 * facing the Darkhold and a single 1-block hole in the middle of its lid. A challenge
 * (Guardian Post or Endermite spawner) stands at the bottom.
 */
public final class DarkholdTowerPiece extends StructurePiece {

    private final int x0, y0, z0, floors;
    private final Direction door;

    public DarkholdTowerPiece(int x0, int y0, int z0, int floors, Direction door) {
        super(Darkhold.TOWER, 0, new BlockBox(x0 - 2, y0 - 40, z0 - 2, x0 + 8, y0 + DarkholdStructure.STOREY * floors, z0 + 8));
        this.x0 = x0;
        this.y0 = y0;
        this.z0 = z0;
        this.floors = floors;
        this.door = door;
    }

    public DarkholdTowerPiece(NbtCompound nbt) {
        super(Darkhold.TOWER, nbt);
        this.x0 = nbt.getInt("X");
        this.y0 = nbt.getInt("Y");
        this.z0 = nbt.getInt("Z");
        this.floors = nbt.getInt("Floors");
        this.door = Direction.byId(nbt.getInt("Door"));
    }

    @Override
    protected void writeNbt(StructureContext context, NbtCompound nbt) {
        nbt.putInt("X", x0);
        nbt.putInt("Y", y0);
        nbt.putInt("Z", z0);
        nbt.putInt("Floors", floors);
        nbt.putInt("Door", door.getId());
    }

    /** In the tower's 7 x 7 footprint at all? (Corners are cut off.) */
    private static boolean inside(int x, int z) {
        return x >= 0 && x <= 6 && z >= 0 && z <= 6 && !((x == 0 || x == 6) && (z == 0 || z == 6));
    }

    /** Part of the wall: the rim of the footprint, plus the four diagonal blocks that close the cut corners. */
    private static boolean wall(int x, int z) {
        if (!inside(x, z)) return false;
        if (x == 0 || x == 6 || z == 0 || z == 6) return true;
        return (x == 1 || x == 5) && (z == 1 || z == 5);
    }

    @Override
    public void generate(StructureWorldAccess world, StructureAccessor structures, ChunkGenerator generator,
                         Random random, BlockBox box, ChunkPos chunkPos, BlockPos pivot) {
        int top = DarkholdStructure.STOREY * floors;
        for (int x = 0; x <= 6; x++) {
            for (int z = 0; z <= 6; z++) {
                if (!inside(x, z)) continue;
                DarkholdBlocks.foot(world, box, x0 + x, y0 - 1, z0 + z);
                for (int y = 0; y <= top; y++) {
                    boolean hole = y == top && x == 3 && z == 3;
                    boolean solid = !hole && (y == 0 || y == top || wall(x, z));
                    DarkholdBlocks.put(world, box, x0 + x, y0 + y, z0 + z,
                            solid ? DarkholdBlocks.OBSIDIAN : DarkholdBlocks.AIR);
                }
            }
        }
        // The doorway, in the middle of the side facing the Darkhold, and a clear step outside it.
        BlockPos centre = new BlockPos(x0 + 3, y0 + 1, z0 + 3);
        BlockPos doorway = centre.offset(door, 3);
        for (int step = 0; step <= 2; step++) {
            BlockPos p = doorway.offset(door, step);
            DarkholdBlocks.put(world, box, p.getX(), p.getY(), p.getZ(), DarkholdBlocks.AIR);
            DarkholdBlocks.put(world, box, p.getX(), p.getY() + 1, p.getZ(), DarkholdBlocks.AIR);
            if (step > 0 && box.contains(p.down()) && DarkholdBlocks.soft(world.getBlockState(p.down())))
                world.setBlockState(p.down(), DarkholdBlocks.OBSIDIAN, 2);
        }
        DarkholdChallenges.place(world, centre, box);
    }
}
