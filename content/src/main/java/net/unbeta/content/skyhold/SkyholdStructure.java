package net.unbeta.content.skyhold;

import com.mojang.serialization.Codec;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.loot.LootTables;
import net.minecraft.structure.PoolStructurePiece;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.Direction;
import net.minecraft.structure.StructurePiecesCollector;
import net.minecraft.structure.pool.StructurePool;
import net.minecraft.structure.pool.StructurePoolBasedGenerator;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Lays out one Skyhold: a broad main island and a crowd of satellites around it (all within
 * ~120 blocks of the centre - the farthest a structure can reach). Inside the main island, two
 * jigsaw workshops on one level: the main one from a big hub, and an annex grown on the side
 * of the island the main one didn't reach. Below that level is kept for the End Gate room.
 */
public final class SkyholdStructure extends Structure {

    public static final Codec<SkyholdStructure> CODEC = createCodec(SkyholdStructure::new);

    private static final RegistryKey<StructurePool> START_POOL = pool("skyhold/start");
    private static final RegistryKey<StructurePool> ANNEX_POOL = pool("skyhold/annex_start");
    private static final int WORKSHOP_DEPTH = 7;       // hub, halls, rooms, halls, rooms, shafts, buildings
    private static final int WORKSHOP_REACH = 36;      // blocks from the hub
    private static final int ANNEX_DEPTH = 6;
    private static final int ANNEX_REACH = 22;
    private static final int ANNEX_OFFSET = 20;        // blocks from the island centre, away from the main workshop
    private static final int HUB_BELOW_GRASS = 16;

    public SkyholdStructure(Structure.Config config) {
        super(config);
    }

    private static RegistryKey<StructurePool> pool(String path) {
        return RegistryKey.of(RegistryKeys.TEMPLATE_POOL, new Identifier("unbeta-content", path));
    }

    @Override
    public Optional<Structure.StructurePosition> getStructurePosition(Structure.Context context) {
        ChunkPos chunk = context.chunkPos();
        int cx = chunk.getStartX() + 8;
        int cz = chunk.getStartZ() + 8;
        long seed = context.seed() ^ (chunk.toLong() * 0x9E3779B97F4A7C15L);
        Random rng = new Random(seed);

        // Main island: ~80-96 across at the top, an even cone down to its point near y 200.
        int mainR = 40 + rng.nextInt(9);
        int mainTop = 276 + rng.nextInt(9);
        int mainDepth = mainTop - (196 + rng.nextInt(6));
        List<StructurePiece> islands = new ArrayList<>();
        islands.add(new SkyholdIslandPiece(cx, cz, mainTop, mainR, mainDepth, rng.nextLong()));
        addSatellites(islands, rng, cx, cz, mainR, mainTop, mainDepth);

        // The main workshop, grown from the hub.
        int floor = mainTop - HUB_BELOW_GRASS;
        List<StructurePiece> rooms = new ArrayList<>(
                jigsaw(context, START_POOL, new BlockPos(cx - 10, floor, cz - 10), WORKSHOP_DEPTH, WORKSHOP_REACH));
        StructurePiece hub = rooms.isEmpty() ? null : rooms.get(0);

        // The annex: on the far side from wherever the main workshop ended up, same floor level.
        double sx = 0, sz = 0;
        for (StructurePiece p : rooms) {
            BlockPos c = p.getBoundingBox().getCenter();
            sx += c.getX() - cx;
            sz += c.getZ() - cz;
        }
        double len = Math.hypot(sx, sz);
        double ux = len < 1 ? 1 : -sx / len, uz = len < 1 ? 0 : -sz / len;
        BlockPos annexAt = new BlockPos(cx + (int) Math.round(ux * ANNEX_OFFSET) - 7, floor,
                                        cz + (int) Math.round(uz * ANNEX_OFFSET) - 7);
        List<StructurePiece> annex = jigsaw(context, ANNEX_POOL, annexAt, ANNEX_DEPTH, ANNEX_REACH);
        if (!annex.isEmpty() && !overlaps(annex.get(0), rooms)) {     // the annex hub itself must be clear
            List<StructurePiece> main = new ArrayList<>(rooms);
            for (StructurePiece p : annex) if (!overlaps(p, main)) rooms.add(p);
        }

        // Loot chests where the rooms landed, and the End Gate room under the hub.
        List<StructurePiece> extras = new ArrayList<>();
        addChests(rooms, extras);
        if (hub != null) {
            BlockBox hb = hub.getBoundingBox();
            extras.add(new SkyholdVaultPiece(hb.getCenter().getX(), hb.getCenter().getZ(), hb.getMinY()));
        }

        // The biome check reads this position; the islands themselves float far above it.
        BlockPos at = new BlockPos(cx, context.chunkGenerator().getSeaLevel(), cz);
        return Optional.of(new Structure.StructurePosition(at, collector -> {
            islands.forEach(collector::addPiece);   // rock first...
            rooms.forEach(collector::addPiece);     // ...then hollow the rooms
            extras.forEach(collector::addPiece);    // ...then chests and the vault room
        }));
    }

    /** Chest spots in each room template (template coordinates), turned to wherever the room landed. */
    private static void addChests(List<StructurePiece> rooms, List<StructurePiece> out) {
        for (StructurePiece p : rooms) {
            if (!(p instanceof PoolStructurePiece pool)) continue;
            String name = pool.getPoolElement().toString();
            if (name.contains("skyhold/storeroom")) {
                chest(out, pool, 2, 1, 7, Direction.NORTH, LootTables.STRONGHOLD_CROSSING_CHEST);
                chest(out, pool, 6, 1, 7, Direction.NORTH, LootTables.STRONGHOLD_CROSSING_CHEST);
            } else if (name.contains("skyhold/workshop")) {
                chest(out, pool, 11, 1, 10, Direction.WEST, LootTables.STRONGHOLD_CORRIDOR_CHEST);
            } else if (name.contains("skyhold/library")) {
                chest(out, pool, 3, 1, 9, Direction.NORTH, LootTables.STRONGHOLD_LIBRARY_CHEST);
            }
        }
    }

    private static void chest(List<StructurePiece> out, PoolStructurePiece piece, int x, int y, int z,
                              Direction facing, net.minecraft.util.Identifier loot) {
        BlockRotation rotation = piece.getRotation();
        BlockPos at = piece.getPos().add(StructureTemplate.transform(
                new StructurePlacementData().setRotation(rotation), new BlockPos(x, y, z)));
        out.add(new SkyholdChestPiece(at, rotation.rotate(facing), loot));
    }

    private static boolean overlaps(StructurePiece piece, List<StructurePiece> others) {
        BlockBox box = piece.getBoundingBox();
        for (StructurePiece o : others) if (o.getBoundingBox().intersects(box)) return true;
        return false;
    }

    /** Runs one jigsaw growth and returns its pieces (empty if the pool is missing or nothing fit). */
    private static List<StructurePiece> jigsaw(Structure.Context context, RegistryKey<StructurePool> key,
                                               BlockPos at, int depth, int reach) {
        Optional<RegistryEntry.Reference<StructurePool>> pool =
                context.dynamicRegistryManager().get(RegistryKeys.TEMPLATE_POOL).getEntry(key);
        if (pool.isEmpty()) return new ArrayList<>();
        Optional<Structure.StructurePosition> grown = StructurePoolBasedGenerator.generate(context, pool.get(),
                Optional.empty(), depth, at, false, Optional.empty(), reach);
        if (grown.isEmpty()) return new ArrayList<>();
        StructurePiecesCollector collector = new StructurePiecesCollector();
        grown.get().generator()
                .ifLeft(gen -> gen.accept(collector))
                .ifRight(done -> done.toList().pieces().forEach(collector::addPiece));
        return new ArrayList<>(collector.toList().pieces());
    }

    @Override
    public StructureType<?> getType() {
        return Skyhold.TYPE;
    }

    /** {x, z, reach, bottom, top} of an island already placed. */
    private static boolean clashes(int[] a, int[] b) {
        boolean sideBySide = Math.hypot(a[0] - b[0], a[1] - b[1]) < a[2] + b[2] + 3;
        boolean sameHeight = !(a[4] + 6 < b[3] || b[4] + 6 < a[3]);
        return sideBySide && sameHeight;   // stacked islands are fine, with 6+ blocks of air between
    }

    private static void addSatellites(List<StructurePiece> islands, Random rng, int cx, int cz,
                                      int mainR, int mainTop, int mainDepth) {
        List<int[]> placed = new ArrayList<>();
        placed.add(new int[]{cx, cz, SkyholdIslandPiece.reach(mainR), mainTop - mainDepth - 2, mainTop + 2});

        // Satellites: 18-30 of them, 18-36 across, in a ring 66-96 blocks out, at heights ~200-300.
        int want = 18 + rng.nextInt(13);
        for (int tries = 0; placed.size() - 1 < want && tries < want * 40; tries++) {
            int r = 9 + rng.nextInt(10);
            double angle = rng.nextDouble() * Math.PI * 2;
            int dist = 66 + rng.nextInt(31);
            int x = cx + (int) Math.round(Math.cos(angle) * dist);
            int z = cz + (int) Math.round(Math.sin(angle) * dist);
            int depth = (int) Math.round(r * (1.0 + rng.nextDouble()));
            int lowestTop = 200 + depth;
            int top = lowestTop + rng.nextInt(Math.max(1, 301 - lowestTop));
            int[] island = {x, z, SkyholdIslandPiece.reach(r), top - depth - 2, top + 2};
            boolean clear = true;
            for (int[] other : placed) {
                if (clashes(island, other)) {
                    clear = false;
                    break;
                }
            }
            if (!clear) continue;
            placed.add(island);
            islands.add(new SkyholdIslandPiece(x, z, top, r, depth, rng.nextLong()));
        }
    }
}
