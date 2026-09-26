package net.unbeta.content.zombie;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Drives a mob climbing up out of the ground over RISE_TICKS.
 *
 * <p>No custom animation or renderer is involved. The client already interpolates
 * entity positions smoothly between server updates - that's what makes normal mob
 * movement look fluid - so simply raising Y a little each tick server-side reads as a
 * smooth ascent. Zombies also already hold their arms forward in their idle pose, so
 * the "arms up, clawing out of the earth" look comes for free.
 *
 * <p>While rising the mob has no AI goals, no gravity, and noClip set, so it passes
 * through the block it's emerging from instead of being shoved aside by collision.
 * All three are restored on arrival, and initGoals() is re-run - which repopulates
 * BOTH goalSelector and targetSelector, since ZombieEntity.initGoals calls
 * initCustomGoals, which fills both.
 */
public final class RisingMob {

    /** 40 ticks = 2 seconds, start to finish. */
    public static final int RISE_TICKS = 40;
    /**
     * How far below the destination a mob starts: its own height plus a sliver, so the
     * top of its head is just under the surface and it emerges head first. A zombie
     * (1.95 tall) starts 2.0 down; a chicken (0.7) only 0.75.
     */
    private static double depthFor(MobEntity mob) {
        return mob.getHeight() + 0.05;
    }

    private static final List<RisingMob> ACTIVE = new ArrayList<>();

    private final MobEntity mob;
    private final BlockPos destination;
    private final BlockState groundState;
    private int ticksElapsed = 0;

    private RisingMob(MobEntity mob, BlockPos destination, BlockState groundState) {
        this.mob = mob;
        this.destination = destination;
        this.groundState = groundState;
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(RisingMob::tickAll);
    }

    /**
     * Begin a rise. The mob must already be spawned in the world; this repositions it
     * below ground and suppresses its AI until it surfaces.
     */
    public static void begin(MobEntity mob, ServerWorld world, BlockPos destination) {
        // Rising through a snow layer throws up snow, not the dirt beneath it.
        BlockState cover = world.getBlockState(destination);
        BlockState ground = cover.isOf(net.minecraft.block.Blocks.SNOW)
                ? cover : world.getBlockState(destination.down());

        mob.setNoGravity(true);
        mob.noClip = true;
        mob.setAiDisabled(true);
        mob.goalSelector.clear(g -> true);

        // Re-assert the buried position. The caller should ALSO have positioned the mob
        // here before spawnEntity - if it spawns at ground level and only sinks on the
        // next tick, the client renders one frame of a fully-risen mob first.
        prePosition(mob, destination);

        ACTIVE.add(new RisingMob(mob, destination, ground));
    }

    /**
     * Place a mob at its buried start position. Call this BEFORE world.spawnEntity so
     * the first packet the client ever receives already has it underground.
     */
    public static void prePosition(MobEntity mob, BlockPos destination) {
        mob.refreshPositionAndAngles(
                destination.getX() + 0.5,
                destination.getY() - depthFor(mob),
                destination.getZ() + 0.5,
                mob.getYaw(), 0.0F);
    }

    private static void tickAll(ServerWorld world) {
        Iterator<RisingMob> it = ACTIVE.iterator();
        while (it.hasNext()) {
            RisingMob rising = it.next();
            if (rising.mob.getWorld() != world) continue;   // other dimension, not our tick
            if (rising.mob.isRemoved()) { it.remove(); continue; }
            if (rising.tick(world)) it.remove();
        }
    }

    /** @return true when the rise is finished and this entry should be dropped. */
    private boolean tick(ServerWorld world) {
        ticksElapsed++;
        double progress = (double) ticksElapsed / RISE_TICKS;

        if (progress >= 1.0) {
            finish(world);
            return true;
        }

        double depth = depthFor(mob);
        double y = destination.getY() - depth + (depth * progress);
        mob.refreshPositionAndAngles(
                destination.getX() + 0.5, y, destination.getZ() + 0.5,
                mob.getYaw(), 0.0F);
        mob.setVelocity(0, 0, 0); // suppress any drift while we're driving position

        // Dirt kicked up at the surface, using the block it's emerging through.
        world.spawnParticles(
                new BlockStateParticleEffect(ParticleTypes.BLOCK, groundState),
                destination.getX() + 0.5, destination.getY(), destination.getZ() + 0.5,
                6, 0.35, 0.1, 0.35, 0.02);

        return false;
    }

    private void finish(ServerWorld world) {
        mob.refreshPositionAndAngles(
                destination.getX() + 0.5, destination.getY(), destination.getZ() + 0.5,
                mob.getYaw(), 0.0F);
        mob.setNoGravity(false);
        mob.noClip = false;
        mob.setAiDisabled(false);

        // Repopulates goalSelector AND targetSelector (initGoals -> initCustomGoals).
        RisingMobAccess.reinitGoals(mob);

        // A last puff of earth as it breaks free.
        world.spawnParticles(
                new BlockStateParticleEffect(ParticleTypes.BLOCK, groundState),
                destination.getX() + 0.5, destination.getY() + 0.2, destination.getZ() + 0.5,
                18, 0.4, 0.2, 0.4, 0.06);
    }
}
