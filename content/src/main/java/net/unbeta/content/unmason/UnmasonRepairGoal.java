package net.unbeta.content.unmason;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.unbeta.content.stronghold.Breach;
import net.unbeta.content.stronghold.StrongholdRepairs;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * An assigned mason sprints to its breach and puts it back together, one block every half
 * second. The highest-priority goal an Unmason has: a repair outranks even being stared at.
 *
 * <p>Each mason claims the gap it's working on, and skips gaps another mason has claimed,
 * so a crew spreads across a hole instead of racing for one block. Reach is measured from
 * its eyes, as a player's is - so a gap in the ceiling is in reach from the floor below.
 *
 * <p>It paths to within 2 blocks of a gap, never into it. Gaps it can't reach, or that
 * someone is standing in, are skipped for a while; if it finds itself standing in a gap, it
 * steps out. It never places a block where a living thing is standing.
 */
public class UnmasonRepairGoal extends Goal {

    private static final double SPRINT = 1.6;
    private static final double REACH = 4.0;            // from the eyes, like a player
    private static final double STUCK_REACH = 6.0;
    private static final int PLACE_INTERVAL = 10;       // half a second
    private static final int STUCK_TICKS = 100;         // 5 s out of reach: reach a little further
    private static final int SKIP_TICKS = 100;          // an unreachable gap is skipped for 5 s

    private final UnmasonEntity mason;
    private final Map<BlockPos, Long> skipUntil = new HashMap<>();
    private BlockPos target;
    private int placeCooldown = 0;
    private int pathCooldown = 0;
    private int ticksSinceProgress = 0;

    public UnmasonRepairGoal(UnmasonEntity mason) {
        this.mason = mason;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK, Control.JUMP));
    }

    @Override
    public boolean canStart() {
        return mason.getWorld() instanceof ServerWorld && StrongholdRepairs.jobFor(mason) != null;
    }

    @Override
    public boolean shouldContinue() {
        return canStart();
    }

    @Override
    public void start() {
        skipUntil.clear();
        target = null;
        ticksSinceProgress = 0;
        pathCooldown = 0;
    }

    @Override
    public void stop() {
        target = null;
        StrongholdRepairs.releaseClaims(mason);
        mason.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (!(mason.getWorld() instanceof ServerWorld world)) return;
        Breach job = StrongholdRepairs.jobFor(mason);
        if (job == null) return;
        long now = world.getTime();
        skipUntil.entrySet().removeIf(e -> e.getValue() <= now);

        BlockPos next = chooseGap(world, job);
        if (next == null) {
            stepOutOfGap(world, job); // the only gaps left may be the one we're standing in
            return;
        }

        Vec3d c = Vec3d.ofCenter(next);
        mason.getLookControl().lookAt(c.x, c.y, c.z);
        double dist = mason.getEyePos().distanceTo(c);
        ticksSinceProgress++;
        double reach = ticksSinceProgress > STUCK_TICKS ? STUCK_REACH : REACH;

        if (dist <= reach) {
            mason.getNavigation().stop();
            if (--placeCooldown <= 0 && place(world, job, next)) {
                placeCooldown = PLACE_INTERVAL;
                ticksSinceProgress = 0;
                target = null;
            }
            return;
        }

        // Still out of reach after a long while, even reaching further: skip this gap for now.
        if (ticksSinceProgress > STUCK_TICKS * 2) {
            skipUntil.put(next, now + SKIP_TICKS);
            StrongholdRepairs.releaseClaims(mason);
            ticksSinceProgress = STUCK_TICKS;
            target = null;
            return;
        }

        if (!next.equals(target) || --pathCooldown <= 0) {
            target = next;
            pathCooldown = 20;
            Path path = mason.getNavigation().findPathTo(next, 2); // to within 2 blocks, not into the gap
            if (path == null) {
                skipUntil.put(next, now + SKIP_TICKS);
                StrongholdRepairs.releaseClaims(mason);
                target = null;
                return;
            }
            mason.getNavigation().startMovingAlong(path, SPRINT);
        }
    }

    /**
     * Nearest open gap this mason can take: not skipped, nobody standing in it, and not
     * claimed by another mason. Claims it.
     */
    private BlockPos chooseGap(ServerWorld world, Breach job) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        Vec3d eyes = mason.getEyePos();
        for (BlockPos pos : job.missing.keySet()) {
            if (skipUntil.containsKey(pos)) continue;
            if (StrongholdRepairs.claimedByOther(mason, pos)) continue;
            if (!world.getBlockState(pos).isReplaceable()) continue;
            if (occupied(world, pos)) continue;
            double d = eyes.squaredDistanceTo(Vec3d.ofCenter(pos));
            if (d < bestDist) { bestDist = d; best = pos; }
        }
        if (best != null) StrongholdRepairs.claim(mason, best);
        return best;
    }

    /** If this mason is standing in an open gap, walk a few blocks away so it can be filled. */
    private void stepOutOfGap(ServerWorld world, Breach job) {
        for (BlockPos pos : job.missing.keySet()) {
            if (!world.getBlockState(pos).isReplaceable()) continue;
            if (!mason.getBoundingBox().intersects(new Box(pos))) continue;
            Vec3d away = mason.getPos().subtract(Vec3d.ofCenter(pos));
            away = new Vec3d(away.x, 0, away.z);
            if (away.lengthSquared() < 1.0E-4) away = new Vec3d(1, 0, 0);
            Vec3d dest = mason.getPos().add(away.normalize().multiply(3.0));
            mason.getNavigation().startMovingTo(dest.x, dest.y, dest.z, SPRINT);
            return;
        }
    }

    private boolean occupied(ServerWorld world, BlockPos pos) {
        return !world.getEntitiesByClass(LivingEntity.class, new Box(pos),
                e -> e.isAlive() && !e.isSpectator()).isEmpty();
    }

    private boolean place(ServerWorld world, Breach job, BlockPos pos) {
        if (occupied(world, pos)) return false; // never entomb a living thing
        BlockState state = StrongholdRepairs.replacementFor(job.missing.get(pos));
        world.setBlockState(pos, state, Block.NOTIFY_ALL);
        world.playSound(null, pos, state.getSoundGroup().getPlaceSound(), SoundCategory.BLOCKS, 1.0F, 0.8F);
        mason.swingHand(Hand.MAIN_HAND);
        StrongholdRepairs.placed(world, mason, job.centre, pos);
        return true;
    }
}
