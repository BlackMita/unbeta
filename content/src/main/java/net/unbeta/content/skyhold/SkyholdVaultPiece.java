package net.unbeta.content.skyhold;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.EndPortalFrameBlock;
import net.minecraft.block.HorizontalConnectingBlock;
import net.minecraft.block.LadderBlock;
import net.minecraft.fluid.Fluids;
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
import net.unbeta.content.burntchest.BurntChests;
import net.unbeta.content.stronghold.StrongholdVault;

/**
 * The Skyhold's End Gate room, deep in the island core under the hub: a raised platform with
 * the End Gate frame ring filled with copper, the locked Burnt Chest vault on top, a lava fall
 * behind iron bars in the back wall, and a ladder up through the rock into a hub corner.
 */
public final class SkyholdVaultPiece extends StructurePiece {

    private static final int HALF = 10;        // room is 21 x 21, like the hub above it
    private static final int DROP = 14;        // floor of this room, below the hub's floor
    private static final int CEIL = 2;         // ceiling of this room, below the hub's floor

    private final int cx, cz, hubFloor;

    public SkyholdVaultPiece(int cx, int cz, int hubFloor) {
        super(Skyhold.VAULT, 0, new BlockBox(cx - HALF, hubFloor - DROP, cz - HALF, cx + HALF, hubFloor, cz + HALF + 2));
        this.cx = cx;
        this.cz = cz;
        this.hubFloor = hubFloor;
    }

    public SkyholdVaultPiece(NbtCompound nbt) {
        super(Skyhold.VAULT, nbt);
        this.cx = nbt.getInt("CX");
        this.cz = nbt.getInt("CZ");
        this.hubFloor = nbt.getInt("HubFloor");
    }

    public BlockPos vaultPos() {
        return new BlockPos(cx, hubFloor - DROP + 3, cz);
    }

    @Override
    protected void writeNbt(StructureContext context, NbtCompound nbt) {
        nbt.putInt("CX", cx);
        nbt.putInt("CZ", cz);
        nbt.putInt("HubFloor", hubFloor);
    }

    private static void put(StructureWorldAccess world, BlockBox box, int x, int y, int z, BlockState state) {
        BlockPos pos = new BlockPos(x, y, z);
        if (box.contains(pos)) world.setBlockState(pos, state, 2);
    }

    private static BlockState wall(int x, int y, int z) {
        int h = Math.floorMod(x * 73428767 ^ y * 912931 ^ z * 42317861, 10);
        return h < 7 ? Blocks.BRICKS.getDefaultState() : h < 9 ? Blocks.COBBLESTONE.getDefaultState()
                                                             : Blocks.MOSSY_COBBLESTONE.getDefaultState();
    }

    @Override
    public void generate(StructureWorldAccess world, StructureAccessor structures, ChunkGenerator generator,
                         Random random, BlockBox box, ChunkPos chunkPos, BlockPos pivot) {
        int yF = hubFloor - DROP, yC = hubFloor - CEIL;

        // Shell: smooth stone floor, cobble ceiling, brick walls, air inside.
        for (int x = cx - HALF; x <= cx + HALF; x++) {
            for (int z = cz - HALF; z <= cz + HALF; z++) {
                for (int y = yF; y <= yC; y++) {
                    boolean edge = Math.abs(x - cx) == HALF || Math.abs(z - cz) == HALF;
                    BlockState s = y == yF ? Blocks.SMOOTH_STONE.getDefaultState()
                                 : y == yC ? Blocks.COBBLESTONE.getDefaultState()
                                 : edge ? wall(x, y, z) : Blocks.AIR.getDefaultState();
                    put(world, box, x, y, z, s);
                }
            }
        }

        // Raised platform, the End Gate frame ring (facing inward), filled with copper.
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++) put(world, box, cx + dx, yF + 1, cz + dz, Blocks.SMOOTH_STONE.getDefaultState());
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean ringX = Math.abs(dx) == 2, ringZ = Math.abs(dz) == 2;
                if (ringX && ringZ) continue;                                       // no corners
                if (ringX || ringZ) {
                    Direction in = dz == -2 ? Direction.SOUTH : dz == 2 ? Direction.NORTH
                                 : dx == -2 ? Direction.EAST : Direction.WEST;
                    put(world, box, cx + dx, yF + 2, cz + dz,
                        Blocks.END_PORTAL_FRAME.getDefaultState().with(EndPortalFrameBlock.FACING, in));
                } else {
                    put(world, box, cx + dx, yF + 2, cz + dz, Blocks.COPPER_BLOCK.getDefaultState());
                }
            }
        }

        // Lava fall behind iron bars in the back wall.
        for (int x = cx - 1; x <= cx + 1; x++)
            for (int z = cz + HALF + 1; z <= cz + HALF + 2; z++)
                for (int y = yF; y <= yC; y++) put(world, box, x, y, z, Blocks.COBBLESTONE.getDefaultState());
        BlockState falling = Fluids.FLOWING_LAVA.getFlowing(8, true).getBlockState();
        for (int y = yF; y <= yC - 1; y++) {
            BlockState lava = (y == yF || y == yC - 1) ? Blocks.LAVA.getDefaultState() : falling;
            put(world, box, cx, y, cz + HALF + 1, lava);
        }
        BlockState bars = Blocks.IRON_BARS.getDefaultState()
                .with(HorizontalConnectingBlock.EAST, true).with(HorizontalConnectingBlock.WEST, true);
        for (int y = yF + 1; y <= yC - 1; y++) put(world, box, cx, y, cz + HALF, bars);

        // Ladder from this room up through the rock into the hub's corner.
        int lx = cx - HALF + 2, lz = cz - HALF + 1;
        for (int y = yC; y <= hubFloor; y++) put(world, box, lx, y, lz - 1, Blocks.COBBLESTONE.getDefaultState());
        for (int y = yF + 1; y <= hubFloor; y++)
            put(world, box, lx, y, lz, Blocks.LADDER.getDefaultState().with(LadderBlock.FACING, Direction.SOUTH));

        // The vault, facing the ladder.
        BlockPos vault = vaultPos();
        if (box.contains(vault)) {
            world.setBlockState(vault, BurntChests.BLOCK.getDefaultState().with(ChestBlock.FACING, Direction.NORTH), 2);
            StrongholdVault.onVaultPlaced(world, vault, random);
        }
    }
}
