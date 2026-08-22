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

            // Spawn the zombie
            ZombieEntity zombie = EntityType.ZOMBIE.create(world);
            if (zombie == null) {
                state.clear(chunkPos);
                continue;
            }

            zombie.refreshPositionAndAngles(
                    spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5,
                    world.random.nextFloat() * 360.0F, 0.0F);
            zombie.initialize(world,
                    world.getLocalDifficulty(spawnPos),
                    SpawnReason.MOB_SUMMONED, null, null);
            zombie.setPersistent();
            world.spawnEntity(zombie);

            // Dirt particle burst + sound — "rising from the ground" feel
            world.spawnParticles(ParticleTypes.POOF,
                    spawnPos.getX() + 0.5, spawnPos.getY() + 0.5, spawnPos.getZ() + 0.5,
                    20, 0.3, 0.3, 0.3, 0.05);
            world.playSound(null, spawnPos,
                    SoundEvents.ENTITY_ZOMBIE_AMBIENT,
                    SoundCategory.HOSTILE, 1.0F, 0.7F);

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
