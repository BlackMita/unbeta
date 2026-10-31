package net.unbeta.content.island;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.chunk.placement.StructurePlacementCalculator;

import java.util.ArrayList;
import java.util.List;

/**
 * Island mode. With the game rule unbetaIslandChunks set above 0 (the island's width in chunks),
 * the overworld is one island around 0, 0 in an endless ocean. Moderner Beta's own island
 * shaping makes the coast (so it's real Beta terrain), and past the island the sea floor is
 * flattened at y = 30. From 2 chunks past the shore there are no caves, ores, structures or
 * anything else, except seagrass and kelp (so clamboxes, half as many, and sponges still
 * turn up). The world border sits just far enough out that the island is still in view.
 */
public final class Island {

    private Island() {}

    public static final String RULE_NAME = "unbetaIslandChunks";
    public static GameRules.Key<GameRules.IntRule> RULE;
    /** Hunger Games sets this: every brand-new world is an island this wide. */
    public static int forcedChunks = 0;

    /** Width in chunks (0 = off), and the world seed, as last read from the overworld. */
    private static volatile int chunks = 0;
    private static volatile long seed = 0;
    public static volatile StructurePlacementCalculator CALCULATOR;

    public static final int FLOOR_Y = 30;
    /** Past the shore by this much, nothing is generated but water, stone, sand and seagrass. */
    public static final int DEAD_ZONE = 32;

    public static boolean active() {
        return chunks > 0;
    }

    public static int chunks() {
        return chunks;
    }

    /** Half the island's width, in blocks. */
    public static double radius() {
        return chunks * 8.0;
    }

    /** How far inside the edge the land starts to slope down into the sea. */
    public static double falloff() {
        return Math.max(24.0, Math.min(128.0, radius() * 0.3));
    }

    /** The shore's distance from 0, 0 in this direction: roughly round, but wobbly. */
    public static double edge(double x, double z) {
        double a = Math.atan2(z, x);
        double p1 = phase(1), p2 = phase(2), p3 = phase(3);
        double wob = 0.08 * Math.sin(3 * a + p1) + 0.05 * Math.sin(5 * a + p2) + 0.03 * Math.sin(7 * a + p3);
        return radius() * (1.0 + wob);
    }

    /** The furthest the shore ever reaches. */
    public static double maxEdge() {
        return radius() * 1.16;
    }

    private static double phase(int i) {
        long h = seed * 6364136223846793005L + i * 1442695040888963407L;
        h ^= (h >>> 29);
        return (h & 0xFFFF) / 65536.0 * Math.PI * 2;
    }

    /** 0 inside the island, rising to 1 a little past the shore (how much ocean to make). */
    public static double oceanAmount(double x, double z) {
        double d = Math.sqrt(x * x + z * z);
        double e = edge(x, z);
        double t = (d - (e - 0.4 * falloff())) / falloff();
        return t <= 0 ? 0 : t >= 1 ? 1 : t;
    }

    /**
     * What Moderner Beta adds to its terrain density here: a gentle rise in the middle (so the
     * island isn't swallowed by a Beta ocean that happened to sit at 0, 0) and a fall into the sea
     * around the edge (-200 is Moderner Beta's own "nothing but ocean").
     */
    public static double densityOffset(double x, double z) {
        double t = oceanAmount(x, z);
        if (t > 0) return -200.0 * t * t;
        double inner = edge(x, z) - 0.4 * falloff();
        double k = Math.sqrt(x * x + z * z) / Math.max(1.0, inner);
        return k >= 1 ? 0 : LAND_LIFT * (1 - k * k);
    }

    /** About 10 blocks of extra height in the very middle of the island. */
    private static final double LAND_LIFT = 16.0;

    /** True when this whole chunk is well out to sea: nothing but ocean is made there. */
    public static boolean deadChunk(ChunkPos pos) {
        if (!active()) return false;
        double x = nearest(pos.getStartX(), pos.getEndX()), z = nearest(pos.getStartZ(), pos.getEndZ());
        return Math.sqrt(x * x + z * z) > maxEdge() + DEAD_ZONE;
    }

    /** True when this chunk's middle is off the island (structures can't start there). */
    public static boolean offIsland(ChunkPos pos) {
        if (!active()) return false;
        double x = pos.getCenterX(), z = pos.getCenterZ();
        return oceanAmount(x, z) >= 0.5;
    }

    /** True for a block out in the open sea. */
    public static boolean atSea(World world, BlockPos pos) {
        if (!active() || world.getRegistryKey() != World.OVERWORLD) return false;
        return Math.sqrt((double) pos.getX() * pos.getX() + (double) pos.getZ() * pos.getZ()) > maxEdge();
    }

    private static double nearest(int min, int max) {
        return min > 0 ? min : max < 0 ? max : 0;
    }

    /** Where the world border goes: past the shore by the view distance less a chunk. */
    public static int borderRadius(MinecraftServer server) {
        int view = server.getPlayerManager() != null ? server.getPlayerManager().getViewDistance() : 10;
        return (int) Math.round(radius() + Math.max(2, view - 1) * 16);
    }

    // ------------------------------------------------------------------ the sea floor

    /** After the terrain is made: fill the deep sea up to a flat sand-and-gravel floor at y = 30. */
    public static void flattenSeaFloor(Chunk chunk) {
        if (!active()) return;
        ChunkPos cp = chunk.getPos();
        int minY = chunk.getBottomY();
        BlockPos.Mutable p = new BlockPos.Mutable();
        BlockState stone = Blocks.STONE.getDefaultState(), sand = Blocks.SAND.getDefaultState(), gravel = Blocks.GRAVEL.getDefaultState();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = cp.getStartX() + lx, z = cp.getStartZ() + lz;
                double ocean = oceanAmount(x, z);
                if (ocean < 0.5) continue;
                boolean filled = false;
                for (int y = minY; y < FLOOR_Y; y++) {
                    p.set(x, y, z);
                    BlockState s = chunk.getBlockState(p);
                    if (s.isAir() || s.getBlock() instanceof net.minecraft.block.FluidBlock) {
                        chunk.setBlockState(p, stone, false);
                        filled = true;
                    }
                }
                if (!filled) continue;
                // The top of the new floor: sand, with patches of gravel.
                BlockState top = ((x >> 3) * 31 + (z >> 3) * 17 + (int) (seed & 7)) % 5 == 0 ? gravel : sand;
                for (int y = FLOOR_Y - 3; y < FLOOR_Y; y++) chunk.setBlockState(p.set(x, y, z), top, false);
            }
        }
    }

    // ------------------------------------------------------------------ strongholds on the island

    /** Three strongholds, spread around under the island. */
    public static List<ChunkPos> strongholds() {
        List<ChunkPos> out = new ArrayList<>();
        double r = radius() * 0.45;
        for (int i = 0; i < 3; i++) {
            double a = phase(10) + i * Math.PI * 2 / 3;
            out.add(new ChunkPos((int) Math.floor(Math.cos(a) * r) >> 4, (int) Math.floor(Math.sin(a) * r) >> 4));
        }
        return out;
    }

    /** One spot on the island for a structure that would otherwise be too rare to land on it. */
    public static ChunkPos spotFor(int salt) {
        double a = phase(20 + (salt & 0xFF));
        double r = radius() * (0.25 + 0.3 * ((phase(40 + (salt & 0xFF)) / (Math.PI * 2))));
        return new ChunkPos((int) Math.floor(Math.cos(a) * r) >> 4, (int) Math.floor(Math.sin(a) * r) >> 4);
    }

    // ------------------------------------------------------------------ setting up

    public static void register() {
        RULE = GameRuleRegistry.register(RULE_NAME, GameRules.Category.MISC,
                GameRuleFactory.createIntRule(0, 0, 4096, (server, rule) -> read(server.getOverworld())));

        ServerWorldEvents.LOAD.register((server, world) -> {
            if (world.getRegistryKey() != World.OVERWORLD) return;
            if (forcedChunks > 0 && world.getGameRules().getInt(RULE) == 0) {
                world.getGameRules().get(RULE).set(forcedChunks, server);
            }
            read(world);
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ServerWorld world = server.getOverworld();
            read(world);
            if (!active()) return;
            if (forcedChunks <= 0) {
                WorldBorder b = world.getWorldBorder();
                b.setCenter(0.5, 0.5);
                b.setSize(borderRadius(server) * 2 + 1);
            }
            // If the spawn ended up off the island, bring it to the middle.
            BlockPos spawn = world.getSpawnPos();
            if (oceanAmount(spawn.getX(), spawn.getZ()) > 0.3) {
                int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, 0, 0);
                world.setSpawnPos(new BlockPos(0, y, 0), 0F);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            chunks = 0;
            CALCULATOR = null;
        });
    }

    private static void read(ServerWorld world) {
        if (world == null) return;
        seed = world.getSeed();
        CALCULATOR = world.getChunkManager().getStructurePlacementCalculator();
        chunks = world.getGameRules().getInt(RULE);
    }
}
