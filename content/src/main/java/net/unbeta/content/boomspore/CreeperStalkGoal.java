package net.unbeta.content.boomspore;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

/**
 * Replaces vanilla CreeperIgniteGoal + MeleeAttackGoal with the Unbeta creeper behavior:
 *
 * <ol>
 *   <li><b>STALK:</b> approach the target player ONLY when they're NOT looking at us
 *       (160° forward cone). Walk toward them at normal speed.</li>
 *   <li><b>FREEZE:</b> if the player IS looking at us, stop moving and stay still
 *       (like a Weeping Angel). Back away slowly after a moment.</li>
 *   <li><b>PLANT:</b> when within 2.5 blocks and player not looking → plant a Boom Spore
 *       in the player's inventory. Play sounds.</li>
 *   <li><b>FLEE:</b> after planting, flee from the player until the spore explodes
 *       (or 8 seconds, whichever comes first). Creeper "grins" during this phase.</li>
 * </ol>
 */
public class CreeperStalkGoal extends Goal {

    private static final double PLANT_DISTANCE_SQ = 2.5 * 2.5; // within 2.5 blocks
    private static final double STALK_SPEED = 1.0;
    private static final double FLEE_SPEED = 1.4;
    /** cos(80°) ≈ 0.1736 — the creeper is within the player's 160° forward cone. */
    private static final double LOOK_CONE_COS = Math.cos(Math.toRadians(80.0));
    private static final int FLEE_DURATION_TICKS = 160; // 8 seconds

    private final CreeperEntity creeper;
    private PlayerEntity target;

    private enum State { STALK, FREEZE, PLANT, FLEE, IDLE }
    private State state = State.IDLE;
    private int fleeTimer = 0;
    private int pathCooldown = 0;

    public CreeperStalkGoal(CreeperEntity creeper) {
        this.creeper = creeper;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        var t = creeper.getTarget();
        if (t instanceof PlayerEntity p && p.isAlive() && !p.isSpectator()) {
            this.target = p;
            return true;
        }
        return false;
    }

    @Override
    public boolean shouldContinue() {
        return target != null && target.isAlive() && !target.isSpectator()
                && creeper.getTarget() != null && state != State.IDLE;
    }

    @Override
    public void start() {
        state = State.STALK;
        fleeTimer = 0;
        pathCooldown = 0;
    }

    @Override
    public void stop() {
        state = State.IDLE;
        target = null;
        creeper.getNavigation().stop();
        // Always reset the grin when the goal stops, regardless of reason
        creeper.getDataTracker().set(CreeperDataTracker.HAS_PLANTED, false);
    }

    @Override
    public void tick() {
        if (target == null) return;

        switch (state) {
            case STALK -> tickStalk();
            case FREEZE -> tickFreeze();
            case FLEE -> tickFlee();
            default -> {}
        }
    }

    private void tickStalk() {
        if (isPlayerLookingAtCreeper()) {
            // Player spotted us — freeze
            creeper.getNavigation().stop();
            state = State.FREEZE;
            return;
        }

        double distSq = creeper.squaredDistanceTo(target);

        // Close enough to plant?
        if (distSq <= PLANT_DISTANCE_SQ) {
            plant();
            return;
        }

        // Approach the player
        if (--pathCooldown <= 0) {
            creeper.getNavigation().startMovingTo(target, STALK_SPEED);
            pathCooldown = 10; // re-path every half second
        }
    }

    private void tickFreeze() {
        // Stand still and look at the player
        creeper.getNavigation().stop();
        creeper.getLookControl().lookAt(target, 30.0F, 30.0F);

        if (!isPlayerLookingAtCreeper()) {
            // Player looked away — resume stalking
            state = State.STALK;
            pathCooldown = 0;
        }
    }

    private void plant() {
        if (creeper.getWorld().isClient) return;

        // Create a live boom spore and insert it into the player's inventory
        ItemStack spore = BoomSporeItem.createLive(creeper.getWorld().getTime());

        // Try to insert into inventory — if full, drop it at their feet
        if (!target.getInventory().insertStack(spore)) {
            target.dropItem(spore, false);
        }

        // Play sounds: long fuse hiss + slowed-down item pickup
        creeper.getWorld().playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.ENTITY_CREEPER_PRIMED, SoundCategory.HOSTILE,
                1.0F, 0.7F); // fuse sound, slightly lower pitch
        creeper.getWorld().playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.HOSTILE,
                0.5F, 0.5F); // slowed pickup pop (lower pitch = slower)

        // Mark the creeper as having planted (for the grin texture + flee behavior)
        creeper.getDataTracker().set(
                CreeperDataTracker.HAS_PLANTED, true);

        // Switch to flee
        state = State.FLEE;
        fleeTimer = FLEE_DURATION_TICKS;
    }

    private void tickFlee() {
        fleeTimer--;

        // Run away from the player
        if (--pathCooldown <= 0) {
            Vec3d away = creeper.getPos().subtract(target.getPos()).normalize();
            Vec3d fleeTarget = creeper.getPos().add(away.multiply(16.0));
            creeper.getNavigation().startMovingTo(
                    fleeTarget.x, fleeTarget.y, fleeTarget.z, FLEE_SPEED);
            pathCooldown = 20;
        }

        // Stop fleeing after timer expires
        if (fleeTimer <= 0) {
            creeper.getDataTracker().set(CreeperDataTracker.HAS_PLANTED, false);
            creeper.setTarget(null);
            state = State.IDLE;
        }
    }

    /** True if the player's look direction is within 160° of the creeper's position. */
    private boolean isPlayerLookingAtCreeper() {
        Vec3d lookDir = target.getRotationVec(1.0F); // player's look direction (normalized)
        Vec3d toCreeper = creeper.getPos().subtract(target.getPos());
        double dist = toCreeper.length();
        if (dist < 0.1) return true; // essentially on top of us
        toCreeper = toCreeper.normalize();
        double dot = lookDir.dotProduct(toCreeper);
        return dot > LOOK_CONE_COS; // > cos(80°) means within 160° cone
    }
}
