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
 * A doorway through a room wall and the narrow obsidian passage across the gap to the next
 * room: always 1 wide and 2 high. Step 0 is the first room's wall (where an obsidian door may
 * stand); step {@code length} is the far room's wall. An entrance has no far room: its passage
 * runs out into the open and the ground in front of it is cleared.
 */
public final class DarkholdCorridorPiece extends StructurePiece {

    private final BlockPos start;     // the doorway's lower block, in the first wall
    private final Direction dir;
    private final int length;
    private final boolean door, entrance;

    public DarkholdCorridorPiece(BlockPos start, Direction dir, int length, boolean door, boolean entrance) {
        super(Darkhold.CORRIDOR, 0, box(start, dir, length, entrance));
        this.start = start.toImmutable();
        this.dir = dir;
        this.length = length;
        this.door = door;
        this.entrance = entrance;
    }

    public DarkholdCorridorPiece(NbtCompound nbt) {
        super(Darkhold.CORRIDOR, nbt);
        this.start = BlockPos.fromLong(nbt.getLong("Start"));
        this.dir = Direction.byId(nbt.getInt("Dir"));
        this.length = nbt.getInt("Len");
        this.door = nbt.getBoolean("Door");
        this.entrance = nbt.getBoolean("Entrance");
    }

    private static BlockBox box(BlockPos start, Direction dir, int length, boolean entrance) {
        int reach = entrance ? length + 2 : length;
        BlockPos end = start.offset(dir, reach);
        Direction side = dir.rotateYClockwise();
        BlockPos a = start.offset(side, 1).down();
        BlockPos b = end.offset(side, -1).up(2);
        return BlockBox.create(a, b);
    }

    @Override
    protected void writeNbt(StructureContext context, NbtCompound nbt) {
        nbt.putLong("Start", start.asLong());
        nbt.putInt("Dir", dir.getId());
        nbt.putInt("Len", length);
        nbt.putBoolean("Door", door);
        nbt.putBoolean("Entrance", entrance);
    }

    @Override
    public void generate(StructureWorldAccess world, StructureAccessor structures, ChunkGenerator generator,
                         Random random, BlockBox box, ChunkPos chunkPos, BlockPos pivot) {
        Direction side = dir.rotateYClockwise();
        int reach = entrance ? length + 2 : length;
        for (int step = 0; step <= reach; step++) {
            BlockPos p = start.offset(dir, step);
            boolean wall = step == 0 || (step == length && !entrance);
            boolean outside = entrance && step > length;
            if (!wall && !outside) {
                // The passage: floor below, a wall each side, a lid on top.
                DarkholdBlocks.put(world, box, p.getX(), p.getY() - 1, p.getZ(), DarkholdBlocks.OBSIDIAN);
                for (int s = -1; s <= 1; s += 2) {
                    BlockPos w = p.offset(side, s);
                    DarkholdBlocks.put(world, box, w.getX(), w.getY(), w.getZ(), DarkholdBlocks.OBSIDIAN);
                    DarkholdBlocks.put(world, box, w.getX(), w.getY() + 1, w.getZ(), DarkholdBlocks.OBSIDIAN);
                    DarkholdBlocks.put(world, box, w.getX(), w.getY() + 2, w.getZ(), DarkholdBlocks.OBSIDIAN);
                    DarkholdBlocks.put(world, box, w.getX(), w.getY() - 1, w.getZ(), DarkholdBlocks.OBSIDIAN);
                }
                DarkholdBlocks.put(world, box, p.getX(), p.getY() + 2, p.getZ(), DarkholdBlocks.OBSIDIAN);
            }
            // The 1 x 2 opening itself.
            DarkholdBlocks.put(world, box, p.getX(), p.getY(), p.getZ(), DarkholdBlocks.AIR);
            DarkholdBlocks.put(world, box, p.getX(), p.getY() + 1, p.getZ(), DarkholdBlocks.AIR);
            if (outside) {
                // Ground in front of an entrance: anything solid in the way is cleared, a
                // missing step is filled.
                BlockPos below = p.down();
                if (box.contains(below) && DarkholdBlocks.soft(world.getBlockState(below)))
                    world.setBlockState(below, DarkholdBlocks.OBSIDIAN, 2);
            }
        }
        if (door) DarkholdBlocks.door(world, box, start, dir);
    }
}
