package net.unbeta.content.zombie;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Drains sullied chunks back out of the ground.
 *
 * <p>When a player stands in a chunk that remembers deaths, a 5-second beat starts. On
 * each beat one remembered entry, chosen at random, rises out of the ground: always at
 * night, with a 1-in-DAY_ODDS chance by day. The chunk is clean once its memory is empty.
 *
 * <p>Beats are tracked per chunk (several players in one chunk don't speed it up) and are
 * not persisted: leaving the chunk and coming back restarts the 5-second wait.
 */
public final class SulliedChunkTick {

    public static final long BEAT_TICKS = 100L; // 5 seconds
    public static final int DAY_ODDS = 8;       // 1-in-8 per beat during the day

    /** world -> (chunk -> world time of that chunk's next beat). */
    private static final Map<RegistryKey<World>, Map<Long, Long>> NEXT_BEAT = new HashMap<>();

    private SulliedChunkTick() {}

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(SulliedChunkTick::tick);
    }

    private static void tick(ServerWorld world) {
        long now = world.getTime();
        if (now % 20 != 0) return; // check once per second

        SulliedChunkState state = SulliedChunkState.getOrCreate(world);
        Map<Long, Long> beats = NEXT_BEAT.computeIfAbsent(world.getRegistryKey(), k -> new HashMap<>());
        Set<Long> occupied = new HashSet<>();

        for (var player : world.getPlayers()) {
            if (player.isSpectator()) continue;
            ChunkPos chunkPos = new ChunkPos(player.getBlockPos());
            long key = chunkPos.toLong();
            if (!occupied.add(key)) continue; // one beat per chunk, however many players

            if (!state.hasMemory(chunkPos)) {
                beats.remove(key);
                continue;
            }

            Long next = beats.get(key);
            if (next == null) {
                beats.put(key, now + BEAT_TICKS); // just stepped in: first beat in 5s
                continue;
            }
            if (now < next) continue;
            beats.put(key, now + BEAT_TICKS);

            boolean isNight = world.getAmbientDarkness() >= 4;
            if (!isNight && world.random.nextInt(DAY_ODDS) != 0) continue;

            riseOne(world, state, chunkPos);
        }

        // Nobody standing in a chunk any more: forget its beat.
        beats.keySet().retainAll(occupied);
    }

    private static void riseOne(ServerWorld world, SulliedChunkState state, ChunkPos chunkPos) {
        BlockPos spawnPos = findSpawnPos(world, chunkPos);
        if (spawnPos == null) return; // no spot this beat; memory kept for the next one

        String id = state.takeRandom(chunkPos, world.random);
        if (id == null) return;

        MobEntity riser = createRiser(world, id, spawnPos);
        if (riser == null) return; // unknown / non-mob type: entry consumed and dropped

        riser.refreshPositionAndAngles(
                spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5,
                world.random.nextFloat() * 360.0F, 0.0F);
        riser.initialize(world, world.getLocalDifficulty(spawnPos),
                SpawnReason.MOB_SUMMONED, null, null);
        riser.setPersistent();
        // Bury BEFORE spawning, so the client never sees a frame of it above ground.
        RisingMob.prePosition(riser, spawnPos);
        world.spawnEntity(riser);
        RisingMob.begin(riser, world, spawnPos);

        world.playSound(null, spawnPos, SoundEvents.BLOCK_ROOTED_DIRT_BREAK,
                SoundCategory.HOSTILE, 1.0F, 0.6F);
    }

    /**
     * Build the mob a remembered entry becomes. A remembered zombie rolls the Unmason odds
     * here, before anything is constructed, so it never changes type mid-rise. Any other
     * id is looked up in the entity registry - which is where the corrupted animals will
     * come from. Unknown ids are checked explicitly, because the entity registry quietly
     * returns its default entry (a pig) for ids it doesn't know.
     */
    private static MobEntity createRiser(ServerWorld world, String id, BlockPos pos) {
        if (SulliedChunkState.ZOMBIE.equals(id)) {
            if (net.unbeta.content.unmason.UnmasonOdds.rollUnmason(world, pos)) {
                return net.unbeta.content.unmason.UnmasonRegistry.UNMASON.create(world);
            }
            ZombieEntity zombie = EntityType.ZOMBIE.create(world);
            if (zombie != null) zombie.addCommandTag("unbeta_presorted");
            return zombie;
        }
        Identifier typeId = Identifier.tryParse(id);
        if (typeId == null || !Registries.ENTITY_TYPE.containsId(typeId)) return null;
        Entity entity = Registries.ENTITY_TYPE.get(typeId).create(world);
        return entity instanceof MobEntity mob ? mob : null;
    }

    /**
     * A spot a mob can rise into: nothing to collide with where its feet and head will be
     * (air, a single snow layer, grass, flowers) and a solid block underneath.
     *
     * <p>This used to require plain air, which silently rejected every column covered by
     * a snow layer, grass or flowers - a snowfield could never produce anything.
     */
    private static BlockPos findSpawnPos(ServerWorld world, ChunkPos chunkPos) {
        // Try 8 random columns in the chunk
        for (int attempt = 0; attempt < 8; attempt++) {
            int x = chunkPos.getStartX() + world.random.nextInt(16);
            int z = chunkPos.getStartZ() + world.random.nextInt(16);
            int y = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            // If the heightmap stopped on top of a passable cover (e.g. a snow layer), step
            // down into it so the mob rises through the cover, not above it.
            for (int i = 0; i < 2 && isPassable(world, pos.down()); i++) pos = pos.down();
            if (isPassable(world, pos) && isPassable(world, pos.up())
                    && world.getBlockState(pos.down()).isSolidBlock(world, pos.down())
                    && !nearScorched(world, pos)) {
                return pos;
            }
        }
        return null;
    }

    /**
     * Nothing to collide with and no fluid. Covers air, a single snow layer, grass and
     * flowers. Snow of two or more layers has real collision and is rejected, since a mob
     * would end its rise partly stuck inside it. Water has no collision shape either,
     * hence the separate fluid check.
     */
    /** Blocks that count as scorched earth: see data/unbeta-content/tags/blocks/scorched.json. */
    public static final net.minecraft.registry.tag.TagKey<net.minecraft.block.Block> SCORCHED =
            net.minecraft.registry.tag.TagKey.of(net.minecraft.registry.RegistryKeys.BLOCK,
                    new net.minecraft.util.Identifier("unbeta-content", "scorched"));
    private static final int SCORCH_RADIUS = 4;

    /**
     * Scorched earth: nothing rises within SCORCH_RADIUS blocks of a burnt, burning or
     * regrowing block. The chunk still remembers its dead (and still puffs spores) - a
     * skipped spot just means the next beat tries elsewhere - so scorching suppresses a
     * chunk rather than cleansing it. Blocks in unloaded neighbouring chunks are skipped,
     * never force-loaded.
     */
    private static boolean nearScorched(ServerWorld world, BlockPos center) {
        for (BlockPos p : BlockPos.iterate(center.add(-SCORCH_RADIUS, -SCORCH_RADIUS, -SCORCH_RADIUS),
                                           center.add(SCORCH_RADIUS, SCORCH_RADIUS, SCORCH_RADIUS))) {
            if (!world.isChunkLoaded(p.getX() >> 4, p.getZ() >> 4)) continue;
            if (world.getBlockState(p).isIn(SCORCHED)) return true;
        }
        return false;
    }

    private static boolean isPassable(ServerWorld world, BlockPos pos) {
        net.minecraft.block.BlockState state = world.getBlockState(pos);
        return state.getCollisionShape(world, pos).isEmpty() && state.getFluidState().isEmpty();
    }
}
