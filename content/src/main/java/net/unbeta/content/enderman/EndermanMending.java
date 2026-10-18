package net.unbeta.content.enderman;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.unbeta.content.mob.MobLightAwareness;
import net.unbeta.core.state.UnbetaWorldState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Endermen put obsidian back. Wherever a player breaks an obsidian block, the spot is
 * remembered (saved with the world); an idle Enderman within 16 blocks will very likely
 * fetch a block of obsidian (you can see it carrying one), walk - or, if that's taking too
 * long, teleport - over, and set it back in place. Anywhere in the world, not just in
 * Darkholds.
 *
 * <p>They won't go near light they can't stand: a spot lit at the Endermen's light threshold
 * (10+) is left alone until it's dark again, and an Enderman standing in such light doesn't
 * take the job.
 */
public final class EndermanMending {

    private static final String SECTION = "enderman_obsidian";
    private static final int MAX_SPOTS = 512;
    private static final int EVERY = 10;              // ticks between checks
    private static final double NOTICE = 16.0;        // how far an Enderman notices a gap
    private static final int TELEPORT_AFTER = 80;     // ticks of walking before it just teleports
    private static final int GIVE_UP_AFTER = 300;
    private static final double REACH = 3.0;

    private static final class Job {
        final BlockPos spot;
        final BlockState carriedBefore;
        int ticks;

        Job(BlockPos spot, BlockState carriedBefore) {
            this.spot = spot;
            this.carriedBefore = carriedBefore;
        }
    }

    private static final Map<EndermanEntity, Job> JOBS = new WeakHashMap<>();

    private EndermanMending() {}

    public static void register() {
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, be) -> {
            if (world instanceof ServerWorld sw && state.isOf(Blocks.OBSIDIAN)) remember(sw, pos);
        });
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (world.getTime() % EVERY == 0) tick(world);
        });
    }

    private static void remember(ServerWorld world, BlockPos pos) {
        UnbetaWorldState.edit(world, SECTION, nbt -> {
            List<Long> spots = new ArrayList<>();
            for (long l : nbt.getLongArray("Spots")) spots.add(l);
            spots.remove(pos.asLong());
            spots.add(pos.asLong());
            while (spots.size() > MAX_SPOTS) spots.remove(0);
            nbt.putLongArray("Spots", spots);
        });
    }

    private static void forget(ServerWorld world, BlockPos pos) {
        UnbetaWorldState.edit(world, SECTION, nbt -> {
            List<Long> spots = new ArrayList<>();
            for (long l : nbt.getLongArray("Spots")) if (l != pos.asLong()) spots.add(l);
            nbt.putLongArray("Spots", spots);
        });
    }

    /** Still a gap worth filling: air (or grass, water and the like) with no one standing in it. */
    private static boolean open(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!(state.isAir() || state.isReplaceable())) return false;
        return world.getOtherEntities(null, new Box(pos), e -> e.isAlive() && !e.isSpectator()).isEmpty();
    }

    private static boolean tooBright(ServerWorld world, BlockPos pos) {
        return MobLightAwareness.inLight(world, pos);
    }

    private static void tick(ServerWorld world) {
        // Carry on with jobs in progress.
        for (Map.Entry<EndermanEntity, Job> e : new ArrayList<>(JOBS.entrySet())) {
            EndermanEntity man = e.getKey();
            Job job = e.getValue();
            if (man.getWorld() != world) continue;
            if (!man.isAlive() || man.getTarget() != null || (job.ticks += EVERY) > GIVE_UP_AFTER
                    || !world.isChunkLoaded(job.spot) || tooBright(world, job.spot)) {
                quit(man, job);
                continue;
            }
            if (!open(world, job.spot)) {
                // Someone filled it (forget it) or is standing in it (try again later).
                BlockState now = world.getBlockState(job.spot);
                if (!now.isAir() && !now.isReplaceable()) forget(world, job.spot);
                quit(man, job);
                continue;
            }
            double d = Math.sqrt(man.squaredDistanceTo(job.spot.toCenterPos()));
            if (d <= REACH) {
                place(world, man, job);
                continue;
            }
            if (job.ticks >= TELEPORT_AFTER && teleportNear(world, man, job.spot)) continue;
            man.getNavigation().startMovingTo(job.spot.getX() + 0.5, job.spot.getY(), job.spot.getZ() + 0.5, 1.0);
        }

        // New jobs: each remembered gap in a loaded, dark place may catch an idle Enderman's eye.
        long[] spots = UnbetaWorldState.read(world, SECTION).getLongArray("Spots");
        if (spots.length == 0) return;
        for (long l : spots) {
            BlockPos spot = BlockPos.fromLong(l);
            if (!world.isChunkLoaded(spot)) continue;
            if (taken(spot)) continue;
            BlockState here = world.getBlockState(spot);
            if (!here.isAir() && !here.isReplaceable()) {
                forget(world, spot);      // filled in by someone else
                continue;
            }
            if (tooBright(world, spot) || world.random.nextInt(3) == 0) continue;   // "highly likely", not certain
            List<EndermanEntity> near = world.getEntitiesByType(EntityType.ENDERMAN, new Box(spot).expand(NOTICE),
                    m -> m.isAlive() && m.getTarget() == null && !JOBS.containsKey(m)
                            && !MobLightAwareness.inLight(world, m.getBlockPos()));
            if (near.isEmpty()) continue;
            EndermanEntity man = near.get(world.random.nextInt(near.size()));
            Job job = new Job(spot, man.getCarriedBlock());
            man.setCarriedBlock(Blocks.OBSIDIAN.getDefaultState());
            JOBS.put(man, job);
        }
    }

    private static boolean taken(BlockPos spot) {
        for (Job j : JOBS.values()) if (j.spot.equals(spot)) return true;
        return false;
    }

    private static void place(ServerWorld world, EndermanEntity man, Job job) {
        world.setBlockState(job.spot, Blocks.OBSIDIAN.getDefaultState());
        BlockSoundGroup sound = BlockSoundGroup.STONE;
        world.playSound(null, job.spot, sound.getPlaceSound(), SoundCategory.HOSTILE, 1.0F, 0.8F);
        man.getLookControl().lookAt(job.spot.toCenterPos());
        forget(world, job.spot);
        quit(man, job);
    }

    /** Hops to a dark, standable spot right next to the gap. */
    private static boolean teleportNear(ServerWorld world, EndermanEntity man, BlockPos spot) {
        for (int i = 0; i < 12; i++) {
            BlockPos to = spot.add(world.random.nextInt(5) - 2, world.random.nextInt(5) - 2, world.random.nextInt(5) - 2);
            if (to.equals(spot) || tooBright(world, to)) continue;
            double x = man.getX(), y = man.getY(), z = man.getZ();
            if (man.teleport(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, true)) {
                world.playSound(null, x, y, z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1.0F, 1.0F);
                man.playSound(SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.0F);
                return true;
            }
        }
        return false;
    }

    private static void quit(EndermanEntity man, Job job) {
        JOBS.remove(man);
        if (man.isAlive()) man.setCarriedBlock(job.carriedBefore);
    }
}
