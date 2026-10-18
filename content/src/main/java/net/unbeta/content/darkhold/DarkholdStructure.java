package net.unbeta.content.darkhold;

import com.mojang.serialization.Codec;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureType;
import net.unbeta.content.skyhold.SkyholdSpawnGuard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Lays out one Darkhold. A small grid of cells (2-3 by 3-4, each 11 x 11 with 3-block gaps
 * between them) is stacked 4-5 floors high, each floor a connected subset of the one below.
 * Every floor change is one stairwell cell spanning both floors. Cells on a floor are grouped
 * into rooms (1, 2, 3 or 4 cells), rooms are joined by 1 x 2 doorways through short passages
 * across the gaps - half of them with an obsidian door - and every cell with nothing above it
 * gets a pyramid roof. One End Gate room sits on the top floor, treasuries on the floors
 * between, and 1-3 watch towers stand apart on the ground.
 *
 * <p>Only on dry, fairly level ground, its ground floor no more than 24 blocks above sea level,
 * and never within 300 blocks of spawn.
 */
public final class DarkholdStructure extends Structure {

    public static final Codec<DarkholdStructure> CODEC = createCodec(DarkholdStructure::new);

    static final int CELL = 11, GAP = 3, PITCH = CELL + GAP, STOREY = 10;   // floor to floor: 9 blocks of headroom
    private static final int MAX_ABOVE_SEA = 24;
    private static final int MAX_SLOPE = 14;
    private static final String[] PLAIN = {"cell_plain", "cell_pillars", "cell_beams"};
    private static final BlockRotation[] TURNS = BlockRotation.values();

    public DarkholdStructure(Structure.Config config) {
        super(config);
    }

    @Override
    public StructureType<?> getType() {
        return Darkhold.TYPE;
    }

    /** A room: floor, corner cell, size in cells, template, turn. */
    private record Room(int f, int i, int j, int w, int d, String template, BlockRotation rotation) {}

    /** A choice of shape for merging cells: size in cells, template, weight. */
    private record Shape(int w, int d, String template, int weight) {}

    private static final Shape[] SHAPES = {
            new Shape(2, 2, "great_hall", 8),
            new Shape(3, 1, "hall_3", 5), new Shape(1, 3, "hall_3", 5),
            new Shape(2, 1, "hall_2", 16), new Shape(1, 2, "hall_2", 16),
            new Shape(1, 1, null, 50),
    };

    @Override
    public Optional<Structure.StructurePosition> getStructurePosition(Structure.Context context) {
        ChunkPos chunk = context.chunkPos();
        int cx = chunk.getStartX() + 8, cz = chunk.getStartZ() + 8;
        if (SkyholdSpawnGuard.tooClose(cx, cz)) return Optional.empty();
        Random rng = new Random(context.seed() ^ (chunk.toLong() * 0xC2B2AE3D27D4EB4FL));

        int W = 2 + rng.nextInt(2), D = 3 + rng.nextInt(2);
        if (rng.nextBoolean()) { int t = W; W = D; D = t; }
        int floors = rng.nextInt(4) == 0 ? 4 : 5;
        int ox = cx - (PITCH * W - GAP) / 2, oz = cz - (PITCH * D - GAP) / 2;

        // The ground: dry under every cell, not too steep, not too high.
        ChunkGenerator gen = context.chunkGenerator();
        int[] heights = new int[W * D];
        for (int i = 0; i < W; i++) {
            for (int j = 0; j < D; j++) {
                int x = ox + PITCH * i + 5, z = oz + PITCH * j + 5;
                int top = gen.getHeightInGround(x, z, Heightmap.Type.WORLD_SURFACE_WG, context.world(), context.noiseConfig());
                int solid = gen.getHeightInGround(x, z, Heightmap.Type.OCEAN_FLOOR_WG, context.world(), context.noiseConfig());
                if (top != solid) return Optional.empty();     // water
                heights[i * D + j] = top;
            }
        }
        int[] sorted = heights.clone();
        Arrays.sort(sorted);
        int base = sorted[sorted.length / 2];
        int sea = gen.getSeaLevel();
        if (sorted[sorted.length - 1] - sorted[0] > MAX_SLOPE) return Optional.empty();
        if (base > sea + MAX_ABOVE_SEA || base < sea - 1) return Optional.empty();

        // Which cells stand on each floor, and where the stairwells go.
        boolean[][][] cells = new boolean[floors][W][D];
        for (boolean[] col : cells[0]) Arrays.fill(col, true);
        trim(cells[0], rng, rng.nextInt(3), 4, -1, -1);
        int[][] stairs = new int[floors - 1][];
        for (int f = 0; f < floors - 1; f++) {
            List<int[]> options = new ArrayList<>();
            for (int i = 0; i < W; i++)
                for (int j = 0; j < D; j++)
                    if (cells[f][i][j] && !(f > 0 && stairs[f - 1][0] == i && stairs[f - 1][1] == j)) options.add(new int[]{i, j});
            stairs[f] = options.get(rng.nextInt(options.size()));
            for (int i = 0; i < W; i++) cells[f + 1][i] = cells[f][i].clone();
            int tries = 0;
            for (int i = 0; i < W; i++) for (int j = 0; j < D; j++) if (rng.nextInt(4) == 0) tries++;
            trim(cells[f + 1], rng, tries, 3, stairs[f][0], stairs[f][1]);
        }

        // Rooms, floor by floor. node[f][i][j]: which room (or stairwell part) each cell belongs to.
        List<Room> rooms = new ArrayList<>();
        int[][][] node = new int[floors][W][D];
        List<int[]> stairNodes = new ArrayList<>();       // {f, i, j} of each stairwell part, by node id - 10000
        boolean treasury = false;
        for (int f = 0; f < floors; f++) {
            for (int[] row : node[f]) Arrays.fill(row, -1);
            if (f < floors - 1) { node[f][stairs[f][0]][stairs[f][1]] = 10000 + stairNodes.size(); stairNodes.add(new int[]{f, stairs[f][0], stairs[f][1]}); }
            if (f > 0) { node[f][stairs[f - 1][0]][stairs[f - 1][1]] = 10000 + stairNodes.size(); stairNodes.add(new int[]{f, stairs[f - 1][0], stairs[f - 1][1]}); }
            List<int[]> free = new ArrayList<>();
            for (int i = 0; i < W; i++) for (int j = 0; j < D; j++) if (cells[f][i][j] && node[f][i][j] < 0) free.add(new int[]{i, j});
            Collections.shuffle(free, rng);
            if (f == floors - 1 && !free.isEmpty()) single(rooms, node, free.remove(0), f, "endgate", rng);
            boolean wantsTreasury = (f > 0 && f < floors - 1 && rng.nextInt(100) < 65) || (f == floors - 1 && !treasury);
            if (wantsTreasury && !free.isEmpty()) { single(rooms, node, free.remove(0), f, "treasury", rng); treasury = true; }
            for (int[] c : free) {
                if (node[f][c[0]][c[1]] >= 0) continue;
                Shape shape = pickShape(rng, cells[f], node[f], c[0], c[1]);
                BlockRotation rot;
                String template = shape.template();
                if (template == null) {
                    template = PLAIN[rng.nextInt(PLAIN.length)];
                    rot = TURNS[rng.nextInt(4)];
                } else if (shape.w() == shape.d()) {
                    rot = TURNS[rng.nextInt(4)];
                } else if (shape.w() > shape.d()) {
                    rot = rng.nextBoolean() ? BlockRotation.NONE : BlockRotation.CLOCKWISE_180;
                } else {
                    rot = rng.nextBoolean() ? BlockRotation.CLOCKWISE_90 : BlockRotation.COUNTERCLOCKWISE_90;
                }
                int id = rooms.size();
                rooms.add(new Room(f, c[0], c[1], shape.w(), shape.d(), template, rot));
                for (int a = 0; a < shape.w(); a++) for (int b = 0; b < shape.d(); b++) node[f][c[0] + a][c[1] + b] = id;
            }
        }

        StructureTemplateManager templates = context.structureTemplateManager();
        List<StructurePiece> footings = new ArrayList<>(), roomPieces = new ArrayList<>(), stairPieces = new ArrayList<>(),
                roofs = new ArrayList<>(), corridors = new ArrayList<>(), towers = new ArrayList<>();

        for (Room r : rooms) {
            int x = ox + PITCH * r.i(), y = base + STOREY * r.f(), z = oz + PITCH * r.j();
            BlockPos at = DarkholdRoomPiece.anchor(templates, r.template(), r.rotation(), new BlockPos(x, y, z));
            roomPieces.add(new DarkholdRoomPiece(templates, r.template(), at, r.rotation()));
            if (r.f() == 0) footings.add(new DarkholdFootingPiece(x, z, x + PITCH * r.w() - GAP - 1, z + PITCH * r.d() - GAP - 1, y));
        }
        for (int f = 0; f < floors - 1; f++) {
            int x = ox + PITCH * stairs[f][0], y = base + STOREY * f, z = oz + PITCH * stairs[f][1];
            stairPieces.add(new DarkholdStairPiece(x, y, z));
            if (f == 0) footings.add(new DarkholdFootingPiece(x, z, x + CELL - 1, z + CELL - 1, y));
        }
        for (int f = 0; f < floors; f++)
            for (int i = 0; i < W; i++)
                for (int j = 0; j < D; j++)
                    if (cells[f][i][j] && (f == floors - 1 || !cells[f + 1][i][j]))
                        roofs.add(new DarkholdRoofPiece(ox + PITCH * i, base + STOREY * f + STOREY + 1, oz + PITCH * j,
                                !endGateAt(rooms, f, i, j)));   // no flame over the End Gate room

        // Doorways: a spanning tree over each floor's rooms, plus a few extra loops.
        for (int f = 0; f < floors; f++) {
            List<int[]> sides = new ArrayList<>();     // {i, j, 0 = east / 1 = south}
            for (int i = 0; i < W; i++) {
                for (int j = 0; j < D; j++) {
                    if (!cells[f][i][j]) continue;
                    if (i + 1 < W && cells[f][i + 1][j] && node[f][i][j] != node[f][i + 1][j]) sides.add(new int[]{i, j, 0});
                    if (j + 1 < D && cells[f][i][j + 1] && node[f][i][j] != node[f][i][j + 1]) sides.add(new int[]{i, j, 1});
                }
            }
            Collections.shuffle(sides, rng);
            UnionFind joined = new UnionFind();
            List<Long> linked = new ArrayList<>();
            List<int[]> chosen = new ArrayList<>();
            for (int[] s : sides) {
                int a = node[f][s[0]][s[1]], b = s[2] == 0 ? node[f][s[0] + 1][s[1]] : node[f][s[0]][s[1] + 1];
                if (joined.union(a, b)) { chosen.add(s); linked.add(pair(a, b)); }
            }
            for (int[] s : sides) {
                int a = node[f][s[0]][s[1]], b = s[2] == 0 ? node[f][s[0] + 1][s[1]] : node[f][s[0]][s[1] + 1];
                if (!linked.contains(pair(a, b)) && rng.nextInt(5) == 0) { chosen.add(s); linked.add(pair(a, b)); }
            }
            int y = base + STOREY * f + 1;
            for (int[] s : chosen) {
                int x = ox + PITCH * s[0], z = oz + PITCH * s[1];
                BlockPos start = s[2] == 0 ? new BlockPos(x + CELL - 1, y, z + 5) : new BlockPos(x + 5, y, z + CELL - 1);
                corridors.add(new DarkholdCorridorPiece(start, s[2] == 0 ? Direction.EAST : Direction.SOUTH,
                        GAP + 1, rng.nextBoolean(), false));
            }
        }

        // Ways in: one or two doorways out of the ground floor.
        List<Object[]> outs = new ArrayList<>();
        for (int i = 0; i < W; i++) {
            for (int j = 0; j < D; j++) {
                if (!cells[0][i][j]) continue;
                for (Direction dir : Direction.Type.HORIZONTAL) {
                    int ni = i + dir.getOffsetX(), nj = j + dir.getOffsetZ();
                    if (ni >= 0 && ni < W && nj >= 0 && nj < D && cells[0][ni][nj]) continue;
                    outs.add(new Object[]{i, j, dir});
                }
            }
        }
        Collections.shuffle(outs, rng);
        int ways = Math.min(outs.size(), rng.nextBoolean() ? 2 : 1);
        for (int k = 0; k < ways; k++) {
            int i = (int) outs.get(k)[0], j = (int) outs.get(k)[1];
            Direction dir = (Direction) outs.get(k)[2];
            int x = ox + PITCH * i + 5 + dir.getOffsetX() * 5, z = oz + PITCH * j + 5 + dir.getOffsetZ() * 5;
            corridors.add(new DarkholdCorridorPiece(new BlockPos(x, base + 1, z), dir, GAP, rng.nextBoolean(), true));
        }

        // Watch towers, standing apart on their own patch of ground.
        int wantTowers = 1 + rng.nextInt(3);
        int minX = ox, maxX = ox + PITCH * W - GAP - 1, minZ = oz, maxZ = oz + PITCH * D - GAP - 1;
        List<int[]> placed = new ArrayList<>();
        for (int tries = 0; placed.size() < wantTowers && tries < 30; tries++) {
            Direction side = Direction.fromHorizontal(rng.nextInt(4));
            int gap = 4 + rng.nextInt(7);
            int tx, tz;
            if (side.getAxis() == Direction.Axis.Z) {
                tx = minX - 3 + rng.nextInt(maxX - minX + 1);
                tz = side == Direction.NORTH ? minZ - gap - 7 : maxZ + gap + 1;
            } else {
                tz = minZ - 3 + rng.nextInt(maxZ - minZ + 1);
                tx = side == Direction.WEST ? minX - gap - 7 : maxX + gap + 1;
            }
            boolean clear = true;
            for (int[] t : placed) if (Math.abs(t[0] - tx) < 12 && Math.abs(t[1] - tz) < 12) clear = false;
            if (!clear) continue;
            int top = gen.getHeightInGround(tx + 3, tz + 3, Heightmap.Type.WORLD_SURFACE_WG, context.world(), context.noiseConfig());
            int solid = gen.getHeightInGround(tx + 3, tz + 3, Heightmap.Type.OCEAN_FLOOR_WG, context.world(), context.noiseConfig());
            if (top != solid || Math.abs(top - base) > 10) continue;
            placed.add(new int[]{tx, tz});
            towers.add(new DarkholdTowerPiece(tx, top, tz, 1 + rng.nextInt(5), side.getOpposite()));
        }

        BlockPos at = new BlockPos(cx, base, cz);
        return Optional.of(new Structure.StructurePosition(at, collector -> {
            footings.forEach(collector::addPiece);
            roomPieces.forEach(collector::addPiece);
            stairPieces.forEach(collector::addPiece);
            roofs.forEach(collector::addPiece);
            corridors.forEach(collector::addPiece);     // last: they cut doorways through the walls
            towers.forEach(collector::addPiece);
        }));
    }

    private static boolean endGateAt(List<Room> rooms, int f, int i, int j) {
        for (Room r : rooms) if (r.f() == f && r.i() == i && r.j() == j && r.template().equals("endgate")) return true;
        return false;
    }

    private static long pair(int a, int b) {
        return a < b ? ((long) a << 32) | b : ((long) b << 32) | a;
    }

    private static void single(List<Room> rooms, int[][][] node, int[] c, int f, String template, Random rng) {
        node[f][c[0]][c[1]] = rooms.size();
        rooms.add(new Room(f, c[0], c[1], 1, 1, template, TURNS[rng.nextInt(4)]));
    }

    /** A weighted-random shape that fits with its corner at (i, j), over free cells of this floor. */
    private static Shape pickShape(Random rng, boolean[][] present, int[][] node, int i, int j) {
        List<Shape> fit = new ArrayList<>();
        int total = 0;
        for (Shape s : SHAPES) {
            boolean ok = true;
            for (int a = 0; a < s.w() && ok; a++) {
                for (int b = 0; b < s.d() && ok; b++) {
                    int x = i + a, z = j + b;
                    ok = x < present.length && z < present[0].length && present[x][z] && node[x][z] < 0;
                }
            }
            if (ok) { fit.add(s); total += s.weight(); }
        }
        int roll = rng.nextInt(total);       // the 1 x 1 always fits
        for (Shape s : fit) {
            roll -= s.weight();
            if (roll < 0) return s;
        }
        return fit.get(fit.size() - 1);
    }

    /** Removes up to n random cells, never (keepI, keepJ), keeping at least min cells, all joined up. */
    private static void trim(boolean[][] floor, Random rng, int n, int min, int keepI, int keepJ) {
        int W = floor.length, D = floor[0].length;
        List<int[]> order = new ArrayList<>();
        for (int i = 0; i < W; i++) for (int j = 0; j < D; j++) order.add(new int[]{i, j});
        Collections.shuffle(order, rng);
        for (int[] c : order) {
            if (n <= 0) return;
            if (!floor[c[0]][c[1]] || (c[0] == keepI && c[1] == keepJ)) continue;
            if (count(floor) <= min) return;
            floor[c[0]][c[1]] = false;
            if (connected(floor)) n--;
            else floor[c[0]][c[1]] = true;
        }
    }

    private static int count(boolean[][] floor) {
        int n = 0;
        for (boolean[] col : floor) for (boolean b : col) if (b) n++;
        return n;
    }

    private static boolean connected(boolean[][] floor) {
        int W = floor.length, D = floor[0].length;
        boolean[][] seen = new boolean[W][D];
        List<int[]> stack = new ArrayList<>();
        outer:
        for (int i = 0; i < W; i++) for (int j = 0; j < D; j++) if (floor[i][j]) { stack.add(new int[]{i, j}); seen[i][j] = true; break outer; }
        int reached = 0;
        while (!stack.isEmpty()) {
            int[] c = stack.remove(stack.size() - 1);
            reached++;
            int[][] next = {{c[0] + 1, c[1]}, {c[0] - 1, c[1]}, {c[0], c[1] + 1}, {c[0], c[1] - 1}};
            for (int[] n : next) {
                if (n[0] < 0 || n[0] >= W || n[1] < 0 || n[1] >= D || !floor[n[0]][n[1]] || seen[n[0]][n[1]]) continue;
                seen[n[0]][n[1]] = true;
                stack.add(n);
            }
        }
        return reached == count(floor);
    }

    /** Tiny union-find over node ids. */
    private static final class UnionFind {
        private final java.util.Map<Integer, Integer> parent = new java.util.HashMap<>();

        int find(int a) {
            int p = parent.getOrDefault(a, a);
            if (p == a) return a;
            int r = find(p);
            parent.put(a, r);
            return r;
        }

        /** Joins a and b; false if they were already joined. */
        boolean union(int a, int b) {
            int ra = find(a), rb = find(b);
            if (ra == rb) return false;
            parent.put(ra, rb);
            return true;
        }
    }
}
