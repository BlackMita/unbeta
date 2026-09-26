package net.unbeta.content.corruption;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.mob.MobEntity;

/**
 * Hunt the closest visible prey (see CorruptionMemory.isPrey): players and healthy
 * livestock alike. While chasing, re-check once a second and switch if something is
 * clearly closer - by at least 2 blocks, so it doesn't flip-flop between near-equal targets.
 *
 * <p>Fixation on attackers isn't handled here: vanilla's RevengeGoal runs at a higher
 * priority, so anything that hits this mob holds its attention until it's out of follow
 * range or out of sight for 3 seconds - then this goal takes over again.
 */
public class ClosestPreyGoal extends ActiveTargetGoal<LivingEntity> {

    private static final int RECHECK_TICKS = 20;
    private static final double SWITCH_MARGIN = 2.0;
    private int recheck = RECHECK_TICKS;

    public ClosestPreyGoal(MobEntity mob) {
        super(mob, LivingEntity.class, 10, true, false, CorruptionMemory::isPrey);
    }

    @Override
    public boolean shouldContinue() {
        if (!super.shouldContinue()) return false;
        if (--this.recheck > 0) return true;
        this.recheck = RECHECK_TICKS;

        LivingEntity current = this.mob.getTarget();
        if (current == null) return true;
        this.findClosestTarget();
        LivingEntity closest = this.targetEntity;
        if (closest == null || closest == current) return true;

        double curDist = Math.sqrt(this.mob.squaredDistanceTo(current));
        double newDist = Math.sqrt(this.mob.squaredDistanceTo(closest));
        if (newDist + SWITCH_MARGIN < curDist) {
            this.mob.setTarget(closest);
            this.target = closest;
        }
        return true;
    }
}
