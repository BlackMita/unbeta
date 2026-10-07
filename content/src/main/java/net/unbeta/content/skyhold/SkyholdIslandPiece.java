package net.unbeta.content.skyhold;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.VineBlock;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.Direction;
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
 * One floating island: a grassy top over a rock body that tapers down to a ragged point. The
 * whole shape comes from the numbers stored here (centre, radius, top, depth, seed), so every
 * chunk the island crosses carves its own slice of the very same island.
 */
public final class SkyholdIslandPiece extends StructurePiece {

    private static final BlockState GRASS = Blocks.GRASS_BLOCK.getDefaultState();
    private static final BlockState DIRT = Blocks.DIRT.getDefaultState();
    private static final BlockState STONE = Blocks.STONE.getDefaultState();
    private static final BlockState COBBLE = Blocks.COBBLESTONE.getDefaultState();
    private static final BlockState MOSSY = Blocks.MOSSY_COBBLESTONE.getDefaultState();
    private static final BlockState COAL = Blocks.COAL_ORE.getDefaultState();
    private static final BlockState IRON = Blocks.IRON_ORE.getDefaultState();

    private final int cx, cz, topY, radius, depth;
    private final long seed;

    public SkyholdIslandPiece(int cx, int cz, int topY, int radius, int depth, long seed) {
        super(Skyhold.ISLAND, 0, boxFor(cx, cz, topY, radius, depth));
        this.cx = cx;
        this.cz = cz;
        this.topY = topY;
        this.radius = radius;
        this.depth = depth;
        this.seed = seed;
    }

    public SkyholdIslandPiece(NbtCompound nbt) {
        super(Skyhold.ISLAND, nbt);
        this.cx = nbt.getInt("CX");
        this.cz = nbt.getInt("CZ");
        this.topY = nbt.getInt("Top");
        this.radius = nbt.getInt("Radius");
        this.depth = nbt.getInt("Depth");
        this.seed = nbt.getLong("Seed");
    }

    /** The farthest the ragged outline can reach from the centre (see generate). */
    static int reach(int radius) {
        return (int) Math.ceil(radius * 1.25) + 1;
    }

    private static BlockBox boxFor(int cx, int cz, int topY, int radius, int depth) {
        int r = reach(radius);
        return new BlockBox(cx - r, topY - depth - 2, cz - r, cx + r, topY + 2, cz + r);
    }

    @Override
    protected void writeNbt(StructureContext context, NbtCompound nbt) {
        nbt.putInt("CX", cx);
        nbt.putInt("CZ", cz);
        nbt.putInt("Top", topY);
        nbt.putInt("Radius", radius);
        nbt.putInt("Depth", depth);
        nbt.putLong("Seed", seed);
    }

    @Override
    public void generate(StructureWorldAccess world, StructureAccessor structures, ChunkGenerator generator,
                         Random random, BlockBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
        int minX = Math.max(boundingBox.getMinX(), chunkBox.getMinX());
        int maxX = Math.min(boundingBox.getMaxX(), chunkBox.getMaxX());
        int minZ = Math.max(boundingBox.getMinZ(), chunkBox.getMinZ());
        int maxZ = Math.min(boundingBox.getMaxZ(), chunkBox.getMaxZ());
        int floorY = Math.max(chunkBox.getMinY(), world.getBottomY());
        int ceilY = Math.min(chunkBox.getMaxY(), world.getTopY() - 1);
        BlockPos.Mutable pos = new BlockPos.Mutable();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                double dx = x - cx, dz = z - cz;
                double dist = Math.sqrt(dx * dx + dz * dz);
                double edge = radius * (0.75 + 0.5 * noise(x, z, 9, seed));       // ragged outline
                if (dist > edge) continue;
                double t = dist / edge;                                            // 0 centre .. 1 rim
                int top = topY + (int) Math.round((noise(x, z, 12, seed + 1) - 0.5) * 3.0) - (t > 0.85 ? 1 : 0);
                double hang = depth * (1.0 - t) * (0.85 + 0.3 * noise(x, z, 5, seed + 2));  // even taper
                int bottom = top - Math.max(2, (int) Math.round(hang));            // tapering underside
                int dirtDepth = 2 + (int) (hash(x, 0, z, seed + 3) * 2);           // 2-3 layers

                for (int y = Math.max(bottom, floorY); y <= Math.min(top, ceilY); y++) {
                    int fromTop = top - y;
                    int fromBottom = y - bottom;
                    BlockState state;
                    if (fromTop == 0) {
                        state = GRASS;
                    } else if (fromTop <= dirtDepth) {
                        state = DIRT;
                    } else {
                        double h = hash(x, y, z, seed + 4);
                        if (fromBottom <= 2 && h < 0.45) state = h < 0.15 ? MOSSY : COBBLE;
                        else if (h > 0.988) state = COAL;
                        else if (h > 0.982) state = IRON;
                        else state = STONE;
                    }
                    world.setBlockState(pos.set(x, y, z), state, 2);
                }
            }
        }
        hangVines(world, chunkBox);
        addRuins(world, chunkBox);
    }

    /** The grass height of this island at (x, z), or MIN_VALUE if (x, z) is off the island. Same maths as generate. */
    private int topAt(int x, int z) {
        double dx = x - cx, dz = z - cz;
        double dist = Math.sqrt(dx * dx + dz * dz);
        double edge = radius * (0.75 + 0.5 * noise(x, z, 9, seed));
        if (dist > edge) return Integer.MIN_VALUE;
        double t = dist / edge;
        return topY + (int) Math.round((noise(x, z, 12, seed + 1) - 0.5) * 3.0) - (t > 0.85 ? 1 : 0);
    }

    private static boolean isRock(BlockState s) {
        return s.isOf(Blocks.STONE) || s.isOf(Blocks.COBBLESTONE) || s.isOf(Blocks.MOSSY_COBBLESTONE)
                || s.isOf(Blocks.COAL_ORE) || s.isOf(Blocks.IRON_ORE) || s.isOf(Blocks.DIRT);
    }

    /** Vines on the island's rock sides and underside, hanging 2-7 blocks. */
    private void hangVines(StructureWorldAccess world, BlockBox chunkBox) {
        int minX = Math.max(boundingBox.getMinX(), chunkBox.getMinX()), maxX = Math.min(boundingBox.getMaxX(), chunkBox.getMaxX());
        int minZ = Math.max(boundingBox.getMinZ(), chunkBox.getMinZ()), maxZ = Math.min(boundingBox.getMaxZ(), chunkBox.getMaxZ());
        int minY = Math.max(boundingBox.getMinY(), chunkBox.getMinY()), maxY = Math.min(topY - 4, chunkBox.getMaxY());
        BlockPos.Mutable p = new BlockPos.Mutable(), n = new BlockPos.Mutable();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    if (hash(x, y, z, seed + 5) >= 0.04) continue;
                    if (!world.getBlockState(p.set(x, y, z)).isAir()) continue;
                    for (Direction d : Direction.Type.HORIZONTAL) {
                        n.set(x + d.getOffsetX(), y, z + d.getOffsetZ());
                        if (!chunkBox.contains(n) || !isRock(world.getBlockState(n))) continue;
                        BooleanProperty face = VineBlock.getFacingProperty(d);
                        int len = 2 + (int) (hash(x, y, z, seed + 6) * 6);
                        for (int k = 0; k < len; k++) {
                            p.set(x, y - k, z);
                            if (!chunkBox.contains(p) || !world.getBlockState(p).isAir()) break;
                            world.setBlockState(p, Blocks.VINE.getDefaultState().with(face, true), 2);
                        }
                        break;
                    }
                }
            }
        }
    }

    /** Brick-and-copper ruins on the island top: walls, banded pillars, fallen chimneys, rubble. */
    private void addRuins(StructureWorldAccess world, BlockBox box) {
        int count = Math.max(1, radius / 6);
        for (int i = 0; i < count; i++) {
            double a = hash(i, 1, 0, seed + 7) * Math.PI * 2;
            double d = Math.sqrt(hash(i, 2, 0, seed + 7)) * radius * 0.85;
            int rx = cx + (int) Math.round(Math.cos(a) * d), rz = cz + (int) Math.round(Math.sin(a) * d);
            boolean alongX = hash(i, 4, 0, seed + 7) < 0.5;
            int ax = alongX ? 1 : 0, az = alongX ? 0 : 1;
            switch ((int) (hash(i, 3, 0, seed + 7) * 4)) {
                case 0 -> {   // broken wall
                    int len = 3 + (int) (hash(i, 5, 0, seed + 7) * 4);
                    for (int j = 0; j < len; j++) {
                        int h = 1 + (int) (hash(i, j, 6, seed + 7) * ((j == 0 || j == len - 1) ? 2 : 4));
                        for (int k = 0; k < h; k++) ruin(world, box, rx + j * ax, k, rz + j * az, mix(i, j, k));
                    }
                }
                case 1 -> {   // pillar with a copper band
                    int h = 3 + (int) (hash(i, 5, 0, seed + 7) * 4);
                    for (int k = 0; k < h; k++)
                        ruin(world, box, rx, k, rz, k == 2 ? Blocks.COPPER_BLOCK.getDefaultState() : Blocks.BRICKS.getDefaultState());
                }
                case 2 -> {   // fallen chimney
                    int len = 4 + (int) (hash(i, 5, 0, seed + 7) * 3);
                    for (int j = 0; j < len; j++)
                        ruin(world, box, rx + j * ax, 0, rz + j * az,
                             j == len - 1 ? Blocks.COPPER_BLOCK.getDefaultState() : Blocks.BRICKS.getDefaultState());
                }
                default -> {  // rubble
                    for (int j = 0; j < 5; j++) {
                        int ox = (int) (hash(i, j, 7, seed + 7) * 7) - 3, oz = (int) (hash(i, j, 8, seed + 7) * 7) - 3;
                        ruin(world, box, rx + ox, 0, rz + oz, mix(i, j, 9));
                    }
                }
            }
        }
    }

    private BlockState mix(int i, int j, int k) {
        double h = hash(i, j, k, seed + 8);
        return h < 0.6 ? Blocks.BRICKS.getDefaultState() : h < 0.85 ? COBBLE : MOSSY;
    }

    /** A ruin block h above the grass at (x, z), if (x, z) is on the island and in this chunk. */
    private void ruin(StructureWorldAccess world, BlockBox box, int x, int h, int z, BlockState state) {
        int top = topAt(x, z);
        if (top == Integer.MIN_VALUE) return;
        BlockPos p = new BlockPos(x, top + 1 + h, z);
        if (box.contains(p)) world.setBlockState(p, state, 2);
    }

    /** Smooth 0..1 value noise on a grid of the given cell size. */
    private static double noise(int x, int z, int cell, long seed) {
        int gx = Math.floorDiv(x, cell), gz = Math.floorDiv(z, cell);
        double fx = (x - gx * cell) / (double) cell, fz = (z - gz * cell) / (double) cell;
        double sx = fx * fx * (3 - 2 * fx), sz = fz * fz * (3 - 2 * fz);
        double a = hash(gx, 0, gz, seed), b = hash(gx + 1, 0, gz, seed);
        double c = hash(gx, 0, gz + 1, seed), d = hash(gx + 1, 0, gz + 1, seed);
        double near = a + (b - a) * sx, far = c + (d - c) * sx;
        return near + (far - near) * sz;
    }

    /** Deterministic 0..1 from a position and a seed. */
    private static double hash(int x, int y, int z, long seed) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L);
        h = (h ^ (h >>> 33)) * 0xFF51AFD7ED558CCDL;
        h = (h ^ (h >>> 33)) * 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }
}
