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
    private net.minecraft.entity.LivingEntity target;

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
        net.minecraft.entity.LivingEntity t = creeper.getTarget();
        if (t == null || !t.isAlive()) return false;

        // If target is a player wearing unlit JoL who hasn't attacked us — don't stalk
        if (t instanceof PlayerEntity p) {
            if (!p.isSpectator()) {
                boolean wearingJol = p.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD)
                        .isOf(net.unbeta.content.jackolantern.JackOLanternRegistry.UNLIT_ITEM);
                boolean attacked = creeper.getAttacker() == p;
                if (wearingJol && !attacked) return false;
                this.target = p;
                return true;
            }
        } else if (t instanceof net.minecraft.entity.LivingEntity) {
            // Non-player attacker (skeleton, zombie, etc.) — retaliate
            this.target = t;
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
        // Only reset grin if we HAVEN'T planted yet.
        // If we have planted, keep HAS_PLANTED=true until the spore explodes.
        if (state != State.FLEE) {
            creeper.getDataTracker().set(CreeperDataTracker.HAS_PLANTED, false);
        }
        state = State.IDLE;
        target = null;
        creeper.getNavigation().stop();
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

        // Create a live boom spore
        ItemStack spore = BoomSporeItem.createLive(creeper.getWorld().getTime());

        if (target instanceof PlayerEntity playerTarget) {
            // Insert into player inventory, drop at feet if full
            if (!playerTarget.getInventory().insertStack(spore)) {
                playerTarget.dropItem(spore, false);
            }
        } else {
            // For mobs, drop the spore at their feet (explodes on landing)
            net.minecraft.entity.ItemEntity drop = new net.minecraft.entity.ItemEntity(
                    creeper.getWorld(),
                    target.getX(), target.getY(), target.getZ(), spore);
            drop.setPickupDelayInfinite();
            creeper.getWorld().spawnEntity(drop);
        }

        // Play sounds: long fuse hiss + slowed-down item pickup
        creeper.getWorld().playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.ENTITY_CREEPER_PRIMED, SoundCategory.HOSTILE, 1.0F, 0.7F);
        creeper.getWorld().playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.HOSTILE, 0.5F, 0.5F);

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
