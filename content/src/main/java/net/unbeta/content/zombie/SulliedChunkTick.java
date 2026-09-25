package net.unbeta.content.zombie;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

public final class SulliedChunkTick {

    private SulliedChunkTick() {}

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(SulliedChunkTick::tick);
    }

    private static void tick(ServerWorld world) {
        long now = world.getTime();
        // Only check every 20 ticks (once per second) for performance
        if (now % 20 != 0) return;

        for (var player : world.getPlayers()) {
            ChunkPos chunkPos = new ChunkPos(player.getBlockPos());
            SulliedChunkState state = SulliedChunkState.getOrCreate(world);

            if (!state.isReady(chunkPos, now)) continue;

            // Day/night check: guaranteed at night, 20% chance in day
            boolean isNight = world.getAmbientDarkness() >= 4;
            if (!isNight && world.random.nextFloat() >= 0.2f) {
                // Daytime roll failed — clear the sullied state anyway
                // so it doesn't keep rolling every second forever
                // Actually keep it so player can still trigger it later
                continue;
            }

            // Find a random solid surface block in this chunk to spawn on
            BlockPos spawnPos = findSpawnPos(world, chunkPos);
            if (spawnPos == null) {
                state.clear(chunkPos);
                continue;
            }

            // Decide zombie-vs-Unmason BEFORE constructing anything. The global swap
            // listener works by discarding a zombie and spawning an Unmason in its place,
            // which would strand our rise controller holding a discarded entity - and
            // would show a zombie turning into an Unmason mid-rise. Deciding up front
            // means whichever mob rises looks like itself the whole way up.
            net.minecraft.entity.mob.MobEntity riser;
            if (net.unbeta.content.unmason.UnmasonOdds.rollUnmason(world, spawnPos)) {
                riser = net.unbeta.content.unmason.UnmasonRegistry.UNMASON.create(world);
            } else {
                ZombieEntity zombie = EntityType.ZOMBIE.create(world);
                // Tell the global swap listener this one has already been rolled for.
                if (zombie != null) zombie.addCommandTag("unbeta_presorted");
                riser = zombie;
            }
            if (riser == null) {
                state.clear(chunkPos);
                continue;
            }

            riser.refreshPositionAndAngles(
                    spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5,
                    world.random.nextFloat() * 360.0F, 0.0F);
            riser.initialize(world,
                    world.getLocalDifficulty(spawnPos),
                    SpawnReason.MOB_SUMMONED, null, null);
            riser.setPersistent();
            // Bury BEFORE spawning, so the client never sees a frame of it above ground.
            net.unbeta.content.zombie.RisingMob.prePosition(riser, spawnPos);
            world.spawnEntity(riser);

            // Climb up out of the earth over 2 seconds, kicking up dirt as it goes.
            net.unbeta.content.zombie.RisingMob.begin(riser, world, spawnPos);

            world.playSound(null, spawnPos,
                    SoundEvents.BLOCK_ROOTED_DIRT_BREAK,
                    SoundCategory.HOSTILE, 1.0F, 0.6F);

            // Clear this chunk — one spawn per sully event
            state.clear(chunkPos);
        }
    }

    private static BlockPos findSpawnPos(ServerWorld world, ChunkPos chunkPos) {
        // Try 8 random columns in the chunk
        for (int attempt = 0; attempt < 8; attempt++) {
            int x = chunkPos.getStartX() + world.random.nextInt(16);
            int z = chunkPos.getStartZ() + world.random.nextInt(16);
            // Find the top solid block
            int y = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            if (world.getBlockState(pos).isAir() &&
                world.getBlockState(pos.down()).isSolidBlock(world, pos.down())) {
                return pos;
            }
        }
        return null;
    }
}
