package net.unbeta.content.darkhold;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoorHinge;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.StructureWorldAccess;
import net.unbeta.content.obsidiandoor.ObsidianDoorRegistry;

/** Small shared helpers for the Darkhold's hand-built pieces. Everything is clipped to the chunk being generated. */
final class DarkholdBlocks {

    static final BlockState OBSIDIAN = Blocks.OBSIDIAN.getDefaultState();
    static final BlockState AIR = Blocks.AIR.getDefaultState();

    private DarkholdBlocks() {}

    static void put(StructureWorldAccess world, BlockBox box, int x, int y, int z, BlockState state) {
        BlockPos pos = new BlockPos(x, y, z);
        if (box.contains(pos)) world.setBlockState(pos, state, 2);
    }

    /** An obsidian door (closed) standing in the 1 x 2 doorway at pos, swinging along dir. */
    static void door(StructureWorldAccess world, BlockBox box, BlockPos pos, Direction dir) {
        BlockState lower = ObsidianDoorRegistry.OBSIDIAN_DOOR.getDefaultState()
                .with(DoorBlock.FACING, dir)
                .with(DoorBlock.HINGE, DoorHinge.LEFT)
                .with(DoorBlock.OPEN, false)
                .with(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        put(world, box, pos.getX(), pos.getY(), pos.getZ(), lower);
        put(world, box, pos.getX(), pos.getY() + 1, pos.getZ(), lower.with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    /** Ground that a footing or doorway should fill or clear: air, water, plants, leaves, snow. */
    static boolean soft(BlockState state) {
        return state.isAir() || state.isReplaceable() || !state.getFluidState().isEmpty()
                || state.isIn(BlockTags.LEAVES);
    }

    /** Fills soft ground under (x, topY, z) with obsidian, down to firm ground (at most 40 blocks). */
    static void foot(StructureWorldAccess world, BlockBox box, int x, int topY, int z) {
        for (int y = topY; y > topY - 40 && y > world.getBottomY(); y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!box.contains(pos)) return;
            if (!soft(world.getBlockState(pos))) return;
            world.setBlockState(pos, OBSIDIAN, 2);
        }
    }

    /** A per-position hash, so choices are the same no matter which chunk asks. */
    static long mix(long seed, BlockPos pos) {
        long h = seed ^ (pos.asLong() * 0x9E3779B97F4A7C15L);
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return h;
    }
}
