package net.unbeta.content.skyhold;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.structure.PoolStructurePiece;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Skyhold's undercroft, at the End Gate room's level: new doorways in the End Gate room
 * (west, east, north - the south wall keeps its lava fall), brick tunnels, and up to three
 * vertical rooms that each rise ~14 blocks and open through the FLOOR of a real workshop
 * room above:
 * <ul>
 *   <li>THE LEAP - stone ledges spiralling up the walls, 3-4 blocks apart: made for Cloud Boots.</li>
 *   <li>THE STAIRWELL - brick stairs winding around a copper column.</li>
 *   <li>THE ORRERY - copper and brick cubes hanging in mid-air, iron bars, a ladder up one wall.</li>
 * </ul>
 * Rooms are chosen from whatever the jigsaw actually built, so every Skyhold is wired differently.
 */
public final class SkyholdUndercroftPiece extends StructurePiece {

    static final int LEAP = 0, STAIRWELL = 1, ORRERY = 2;
    private static final int VAULT_HALF = 10;
    private static final int DROP = 14;     // End Gate room floor, below the hub's floor

    private static final BlockState AIR = Blocks.AIR.getDefaultState();
    private static final BlockState SMOOTH = Blocks.SMOOTH_STONE.getDefaultState();
    private static final BlockState BRICK = Blocks.BRICKS.getDefaultState();
    private static final BlockState COBBLE = Blocks.COBBLESTONE.getDefaultState();
    private static final BlockState MOSSY = Blocks.MOSSY_COBBLESTONE.getDefaultState();
    private static final BlockState COPPER = Blocks.COPPER_BLOCK.getDefaultState();
    private static final BlockState GLOW = Blocks.GLOWSTONE.getDefaultState();

    private final int cx, cz, hubFloor;
    private final int[] chambers;   // type, x, z, type, x, z, ...

    public SkyholdUndercroftPiece(int cx, int cz, int hubFloor, int[] chambers) {
        super(Skyhold.UNDERCROFT, 0, boxFor(cx, cz, hubFloor, chambers));
        this.cx = cx;
        this.cz = cz;
        this.hubFloor = hubFloor;
        this.chambers = chambers;
    }

    public SkyholdUndercroftPiece(NbtCompound nbt) {
        super(Skyhold.UNDERCROFT, nbt);
        this.cx = nbt.getInt("CX");
        this.cz = nbt.getInt("CZ");
        this.hubFloor = nbt.getInt("HubFloor");
        this.chambers = nbt.getIntArray("Chambers");
    }

    @Override
    protected void writeNbt(StructureContext context, NbtCompound nbt) {
        nbt.putInt("CX", cx);
        nbt.putInt("CZ", cz);
        nbt.putInt("HubFloor", hubFloor);
        nbt.putIntArray("Chambers", chambers);
    }

    private static BlockBox boxFor(int cx, int cz, int hubFloor, int[] ch) {
        int minX = cx - 16, maxX = cx + 16, minZ = cz - 16, maxZ = cz + 16;
        for (int i = 0; i < ch.length; i += 3) {
            minX = Math.min(minX, ch[i + 1] - 5); maxX = Math.max(maxX, ch[i + 1] + 5);
            minZ = Math.min(minZ, ch[i + 2] - 5); maxZ = Math.max(maxZ, ch[i + 2] + 5);
        }
        return new BlockBox(minX, hubFloor - DROP, minZ, maxX, hubFloor, maxZ);
    }

    /** Chooses up to three workshop rooms to climb into. Null if none suit. */
    public static SkyholdUndercroftPiece plan(List<StructurePiece> rooms, StructurePiece hub, long seed) {
        BlockBox hb = hub.getBoundingBox();
        int cx = hb.getCenter().getX(), cz = hb.getCenter().getZ(), floor = hb.getMinY();
        List<int[]> candidates = new ArrayList<>();
        for (StructurePiece p : rooms) {
            if (p == hub || !(p instanceof PoolStructurePiece pool)) continue;
            String n = pool.getPoolElement().toString();
            if (!(n.contains("skyhold/workshop") || n.contains("skyhold/library") || n.contains("skyhold/storeroom")
                    || n.contains("skyhold/annex") || n.contains("skyhold/junction"))) continue;
            BlockBox b = p.getBoundingBox();
            if (b.getMinY() != floor || b.getBlockCountX() < 7 || b.getBlockCountZ() < 7) continue;
            BlockPos c = b.getCenter();
            if (Math.max(Math.abs(c.getX() - cx), Math.abs(c.getZ() - cz)) < 15) continue;   // clear of the End Gate room
            candidates.add(new int[]{c.getX(), c.getZ()});
        }
        Collections.shuffle(candidates, new java.util.Random(seed ^ 0x5EEDC0DEL));
        List<int[]> chosen = new ArrayList<>();
        for (int[] c : candidates) {
            boolean clear = true;
            for (int[] o : chosen) if (Math.max(Math.abs(c[0] - o[0]), Math.abs(c[1] - o[1])) < 10) clear = false;
            if (clear) chosen.add(c);
            if (chosen.size() == 3) break;
        }
        if (chosen.isEmpty()) return null;
        int[] out = new int[chosen.size() * 3];
        for (int i = 0; i < chosen.size(); i++) {
            out[i * 3] = i;                     // the first is always The Leap
            out[i * 3 + 1] = chosen.get(i)[0];
            out[i * 3 + 2] = chosen.get(i)[1];
        }
        return new SkyholdUndercroftPiece(cx, cz, floor, out);
    }

    // ------------------------------------------------------------------ generation

    @Override
    public void generate(StructureWorldAccess world, StructureAccessor structures, ChunkGenerator generator,
                         Random random, BlockBox box, ChunkPos chunkPos, BlockPos pivot) {
        int yF = hubFloor - DROP;
        for (int i = 0; i < chambers.length; i += 3) chamber(world, box, chambers[i], chambers[i + 1], chambers[i + 2], yF);
        for (int i = 0; i < chambers.length; i += 3) {
            int tx = chambers[i + 1], tz = chambers[i + 2];
            int dx = tx - cx, dz = tz - cz;
            // the doorway in the End Gate room
            if (Math.abs(dx) >= Math.abs(dz) || dz > 0) {
                int side = Math.abs(dx) >= Math.abs(dz) ? Integer.signum(dx) : (dx >= 0 ? 1 : -1);
                if (side == 0) side = 1;
                for (int o = -1; o <= 1; o++) for (int y = yF + 1; y <= yF + 3; y++) put(world, box, cx + side * VAULT_HALF, y, cz + o, AIR);
            } else {
                for (int o = -1; o <= 1; o++) for (int y = yF + 1; y <= yF + 3; y++) put(world, box, cx + o, y, cz - VAULT_HALF, AIR);
            }
            tunnel(world, box, path(tx, tz), yF);
        }
    }

    /** Door -> chamber, never through the End Gate room or its lava (south). Each point: x, z, dirX, dirZ. */
    private List<int[]> path(int tx, int tz) {
        List<int[]> pts = new ArrayList<>();
        int dx = tx - cx, dz = tz - cz;
        if (Math.abs(dx) >= Math.abs(dz)) {
            int s = dx >= 0 ? 1 : -1;
            line(pts, cx + s * (VAULT_HALF + 1), cz, tx, cz);
            line(pts, tx, cz, tx, tz);
        } else if (dz < 0) {
            line(pts, cx, cz - VAULT_HALF - 1, cx, tz);
            line(pts, cx, tz, tx, tz);
        } else {
            int s = dx >= 0 ? 1 : -1;
            int x1 = cx + s * (VAULT_HALF + 3);
            line(pts, cx + s * (VAULT_HALF + 1), cz, x1, cz);
            line(pts, x1, cz, x1, tz);
            line(pts, x1, tz, tx, tz);
        }
        return pts;
    }

    private static void line(List<int[]> pts, int x0, int z0, int x1, int z1) {
        int sx = Integer.signum(x1 - x0), sz = Integer.signum(z1 - z0);
        int x = x0, z = z0;
        while (true) {
            pts.add(new int[]{x, z, sx, sz});
            if (x == x1 && z == z1) break;
            x += sx;
            z += sz;
        }
    }

    private int chamberAt(int x, int z) {
        for (int i = 0; i < chambers.length; i += 3) {
            if (Math.abs(x - chambers[i + 1]) <= 3 && Math.abs(z - chambers[i + 2]) <= 3) return i;
        }
        return -1;
    }

    private boolean onChamberWall(int i, int x, int z) {
        return Math.abs(x - chambers[i + 1]) == 3 || Math.abs(z - chambers[i + 2]) == 3;
    }

    private void tunnel(StructureWorldAccess world, BlockBox box, List<int[]> pts, int yF) {
        int n = 0;
        for (int[] p : pts) {
            int px = p[2] != 0 ? 0 : 1, pz = p[2] != 0 ? 1 : 0;      // perpendicular to travel
            for (int o = -2; o <= 2; o++) {
                int x = p[0] + px * o, z = p[1] + pz * o;
                int ch = chamberAt(x, z);
                if (ch >= 0) {
                    // Through a chamber's wall only: a doorway, never its insides.
                    if (Math.abs(o) <= 1 && onChamberWall(ch, x, z)) {
                        for (int y = yF + 1; y <= yF + 3; y++) put(world, box, x, y, z, AIR);
                    }
                    continue;
                }
                if (Math.abs(o) <= 1) {
                    put(world, box, x, yF, z, SMOOTH);
                    for (int y = yF + 1; y <= yF + 3; y++) put(world, box, x, y, z, AIR);
                    BlockState roof = (o == 0 && n % 7 == 3) ? GLOW : BRICK;
                    solid(world, box, x, yF + 4, z, roof);
                } else {
                    for (int y = yF; y <= yF + 4; y++) solid(world, box, x, y, z, wall(x, y, z));
                }
            }
            n++;
        }
    }

    private void chamber(StructureWorldAccess world, BlockBox box, int type, int x, int z, int yF) {
        int top = hubFloor;    // the room above's floor: this room's ceiling
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                boolean edge = Math.abs(dx) == 3 || Math.abs(dz) == 3;
                for (int y = yF; y < top; y++) {
                    BlockState s = y == yF ? SMOOTH : edge ? wall(x + dx, y, z + dz) : AIR;
                    put(world, box, x + dx, y, z + dz, s);
                }
            }
        }
        // light
        put(world, box, x - 3, yF + 6, z + 2, GLOW);
        put(world, box, x + 3, yF + 10, z - 2, GLOW);

        switch (type) {
            case LEAP -> {
                // Ledges climbing the walls, 3-4 blocks apart; the last sits under the hole.
                for (int dz = -2; dz <= 1; dz++) put(world, box, x - 2, yF + 3, z + dz, SMOOTH);       // west + corner
                for (int dx = -1; dx <= 2; dx++) put(world, box, x + dx, yF + 7, z - 2, SMOOTH);       // north + corner
                for (int dx = 1; dx <= 2; dx++) for (int dz = -1; dz <= 1; dz++) put(world, box, x + dx, yF + 11, z + dz, SMOOTH);
                put(world, box, x + 3, yF + 7, z - 2, Blocks.COPPER_BLOCK.getDefaultState());
                hole(world, box, x - 1, x + 1, z - 1, z + 1, top);
            }
            case STAIRWELL -> {
                for (int y = yF + 1; y <= top; y++) put(world, box, x, y, z, COPPER);
                int[][] ring = {{-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}};
                Direction[] next = {Direction.EAST, Direction.EAST, Direction.SOUTH, Direction.SOUTH,
                                    Direction.WEST, Direction.WEST, Direction.NORTH, Direction.NORTH};
                for (int k = 0; k < top - yF - 1; k++) {
                    int r = k % 8;
                    put(world, box, x + ring[r][0], yF + 1 + k, z + ring[r][1],
                        Blocks.BRICK_STAIRS.getDefaultState().with(StairsBlock.FACING, next[r]));
                }
                for (int[] c : ring) put(world, box, x + c[0], top, z + c[1], AIR);
            }
            default -> {   // ORRERY
                for (int y = yF + 1; y <= top; y++) {
                    put(world, box, x + 2, y, z, Blocks.LADDER.getDefaultState().with(LadderBlock.FACING, Direction.WEST));
                }
                hole(world, box, x + 1, x + 2, z - 1, z + 1, top);
                put(world, box, x + 2, top, z, Blocks.LADDER.getDefaultState().with(LadderBlock.FACING, Direction.WEST));
                for (int dx = -2; dx <= 1; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        double h = hash(x + dx, 0, z + dz);
                        if (h < 0.28) {                     // a floating cube
                            int y = yF + 5 + (int) (hash(x + dx, 1, z + dz) * 6);
                            put(world, box, x + dx, y, z + dz, h < 0.12 ? COPPER : h < 0.24 ? BRICK : GLOW);
                        } else if (h > 0.86 && dx < 1) {   // iron bars hanging from above
                            int len = 2 + (int) (hash(x + dx, 2, z + dz) * 4);
                            for (int k = 1; k <= len; k++) put(world, box, x + dx, top - k, z + dz, Blocks.IRON_BARS.getDefaultState());
                        }
                    }
                }
            }
        }
    }

    private void hole(StructureWorldAccess world, BlockBox box, int x0, int x1, int z0, int z1, int y) {
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) put(world, box, x, y, z, AIR);
    }

    private static void put(StructureWorldAccess world, BlockBox box, int x, int y, int z, BlockState s) {
        BlockPos p = new BlockPos(x, y, z);
        if (box.contains(p)) world.setBlockState(p, s, 2);
    }

    /** Only where there is something to replace - never fills open space. */
    private static void solid(StructureWorldAccess world, BlockBox box, int x, int y, int z, BlockState s) {
        BlockPos p = new BlockPos(x, y, z);
        if (box.contains(p) && !world.getBlockState(p).isAir()) world.setBlockState(p, s, 2);
    }

    private static BlockState wall(int x, int y, int z) {
        double h = hash(x, y, z);
        return h < 0.7 ? BRICK : h < 0.9 ? COBBLE : MOSSY;
    }

    private static double hash(int x, int y, int z) {
        long h = (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L) ^ 0x5EEDL;
        h = (h ^ (h >>> 33)) * 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }
}
