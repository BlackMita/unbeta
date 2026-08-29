package net.unbeta.content.unlikelike;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;

import java.util.EnumSet;

/**
 * Phase A placeholder: detects when a player is within grab range.
 * Phase B will implement the actual grab/ride/spit mechanic.
 */
public class UnlikeLikeGrabGoal extends Goal {

    private final UnlikeLikeEntity unlikeLike;
    private PlayerEntity target;

    public UnlikeLikeGrabGoal(UnlikeLikeEntity unlikeLike) {
        this.unlikeLike = unlikeLike;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (!(unlikeLike.getTarget() instanceof PlayerEntity player)) return false;
        if (player.isCreative() || player.isSpectator()) return false;
        double dist = unlikeLike.squaredDistanceTo(player);
        return dist <= 9.0; // within 3 blocks
    }

    @Override
    public void start() {
        target = (PlayerEntity) unlikeLike.getTarget();
        unlikeLike.startGrab(target);
    }

    @Override
    public boolean shouldContinue() {
        return false; // Phase B will manage continuation
    }
}
