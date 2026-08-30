package net.unbeta.content.unlikelike;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import java.util.EnumSet;

public class UnlikeLikeGrabGoal extends Goal {

    private final UnlikeLikeEntity unlikeLike;
    private PlayerEntity target;
    private int grabTimer;
    private static final int GRAB_COOLDOWN = 80;

    public UnlikeLikeGrabGoal(UnlikeLikeEntity unlikeLike) {
        this.unlikeLike = unlikeLike;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK, Control.JUMP));
    }

    @Override
    public boolean canStart() {
        if (unlikeLike.grabCooldown > 0) return false;
        if (!(unlikeLike.getTarget() instanceof PlayerEntity player)) return false;
        if (player.isCreative() || player.isSpectator()) return false;
        return unlikeLike.squaredDistanceTo(player) <= 9.0;
    }

    @Override
    public boolean shouldContinue() {
        return grabTimer > 0 && target != null && !target.isDead();
    }

    @Override
    public void start() {
        target = (PlayerEntity) unlikeLike.getTarget();
        if (target == null) return;

        // If the player is in a vehicle, deal with it before grabbing
        if (target.hasVehicle()) {
            net.minecraft.entity.Entity vehicle = target.getVehicle();
            target.stopRiding();
            if (vehicle instanceof net.minecraft.entity.vehicle.BoatEntity
                    || vehicle instanceof net.minecraft.entity.vehicle.AbstractMinecartEntity) {
                // Non-living vehicle → drop-ify it
                net.minecraft.item.ItemStack drop = vehicle.getPickBlockStack();
                if (drop != null && !drop.isEmpty()) {
                    net.minecraft.block.Block.dropStack(
                            vehicle.getWorld(), vehicle.getBlockPos(), drop);
                }
                vehicle.discard();
            }
            // Living vehicles (pig/horse) just get the player dismounted — already done above
        }
        target.addStatusEffect(new StatusEffectInstance(
                StatusEffects.BLINDNESS, UnlikeLikeEntity.GRAB_DURATION * 2 + 20,
                0, false, false));
        grabTimer = UnlikeLikeEntity.GRAB_DURATION;
        unlikeLike.grabbedPlayer = target;
        unlikeLike.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (target == null) return;
        // Freeze the Unlike-Like in place during grab
        unlikeLike.getNavigation().stop();
        unlikeLike.setVelocity(0, 0, 0);
        // Smoothly pull player toward Unlike-Like instead of hard teleport
        Vec3d ulPos = unlikeLike.getPos();
        Vec3d pPos = target.getPos();
        double dx = ulPos.x - pPos.x;
        double dy = ulPos.y - pPos.y;
        double dz = ulPos.z - pPos.z;
        target.setVelocity(dx * 0.5, dy * 0.5, dz * 0.5);
        target.velocityModified = true;
        grabTimer--;
        if (grabTimer <= 0) spitOut();
    }

    @Override
    public void stop() {
        unlikeLike.grabbedPlayer = null;
        target = null;
        grabTimer = 0;
    }

    private void spitOut() {
        if (target == null) return;

        // Blindness fades naturally while player is mid-air

        // Spew sound at spit
        unlikeLike.playSound(UnlikeLikeSounds.SPEW, 1.0F, 1.0F);

        // Fling player away hard
        Vec3d dir = target.getPos().subtract(unlikeLike.getPos()).normalize();
        if (dir.lengthSquared() < 0.01) dir = new Vec3d(1, 0, 0);
        target.teleport(target.getX() + dir.x * 2, target.getY() + 0.5, target.getZ() + dir.z * 2);
        target.setVelocity(dir.x * 2.5, 0.8, dir.z * 2.5);
        target.velocityModified = true;

        // Steal
        int grabs = unlikeLike.getGrabCount();
        unlikeLike.incrementGrabCount();
        float chance = switch (grabs) {
            case 0 -> 1.00f;
            case 1 -> 0.75f;
            case 2 -> 0.50f;
            case 3 -> 0.25f;
            default -> 0.02f;
        };
        if (target.getRandom().nextFloat() < chance) {
            ItemStack stolen = UnlikeLikeTheft.stealFrom(target);
            if (!stolen.isEmpty()) unlikeLike.addStolenItem(stolen);
        }

        // Cooldown then re-acquire
        unlikeLike.grabCooldown = GRAB_COOLDOWN;
        unlikeLike.setTarget(target);
        target = null;
        grabTimer = 0;
    }
}
