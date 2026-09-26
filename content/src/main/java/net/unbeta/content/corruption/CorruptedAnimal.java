package net.unbeta.content.corruption;

import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Marker for livestock that zombie corruption has taken: zombie cow, pig, sheep, chicken.
 * Each keeps its vanilla body, health and speed, but hunts players like a zombie.
 *
 * <p>Other systems check for this interface: the JoL flee mixin (corrupted animals don't
 * flee from a Jack o'Lantern) and the JoL disguise mixin (the disguise fools them, as it
 * fools zombies).
 */
public interface CorruptedAnimal {

    /** Zombie-style AI in place of the animal's passive goals. */
    static void addHostileGoals(PathAwareEntity mob, GoalSelector goals, GoalSelector targets) {
        goals.add(1, new SwimGoal(mob));
        goals.add(2, new MeleeAttackGoal(mob, 1.0, false));
        goals.add(7, new WanderAroundFarGoal(mob, 1.0));
        goals.add(8, new LookAtEntityGoal(mob, PlayerEntity.class, 8.0F));
        goals.add(8, new LookAroundGoal(mob));
        targets.add(1, new RevengeGoal(mob));
        targets.add(2, new ClosestPreyGoal(mob)); // players and healthy livestock
    }

    /**
     * The animal's own attributes plus what a hunter needs: attack damage (vanilla
     * animals have none, and melee attacks crash without it) and a zombie's 35-block
     * follow range.
     */
    static DefaultAttributeContainer.Builder hostile(DefaultAttributeContainer.Builder base,
                                                     double attackDamage) {
        return base.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, attackDamage)
                   .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 35.0);
    }
}
