package net.unbeta.content.unmason;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.unbeta.content.stronghold.StrongholdSpace;

import java.util.EnumSet;

/**
 * Inside the stronghold, an Unmason follows the player who last looked at it - but only
 * while that player isn't looking. It advances whenever you turn away and stops dead the
 * moment you turn back (UnmasonFreezeGoal, which sits above this).
 *
 * <p>The rules:
 * <ul>
 *   <li>Only inside the stronghold - this is what replaces walking toward the centre.</li>
 *   <li>Follows the player who last looked at it: a glance is what marks you.</li>
 *   <li>Must be able to see that player - round a corner and it loses you and wanders off.</li>
 *   <li>Starts within 16 blocks, gives up past 16, closes to 5 and holds.</li>
 *   <li>Normal gentle speed; never leaves the stronghold.</li>
 * </ul>
 */
public class UnmasonFollowGoal extends Goal {

    private static final double START_RANGE = 16.0;
    private static final double HOLD_DISTANCE = 5.0;
    private static final double SPEED = 1.0;
    /** cos(80 degrees): the same 160-degree cone the freeze uses. */
    private static final double LOOK_CONE_COS = Math.cos(Math.toRadians(80.0));

    private final UnmasonEntity unmason;
    private PlayerEntity following;
    private int pathCooldown = 0;

    public UnmasonFollowGoal(UnmasonEntity unmason) {
        this.unmason = unmason;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        PlayerEntity p = candidate();
        if (p == null) return false;
        this.following = p;
        return true;
    }

    @Override
    public boolean shouldContinue() {
        return candidate() != null;
    }

    @Override
    public void stop() {
        following = null;
        pathCooldown = 0;
        unmason.getNavigation().stop();
    }

    @Override
    public void tick() {
        PlayerEntity target = candidate();
        if (target == null) return;
        this.following = target;

        unmason.getLookControl().lookAt(target, 30.0F, 30.0F);

        // Being looked at is the freeze goal's business - it outranks this one and will have
        // taken over. Here we only have to stop once we're close enough.
        double dist = Math.sqrt(unmason.squaredDistanceTo(target));
        if (dist <= HOLD_DISTANCE) {
            unmason.getNavigation().stop();
            return;
        }
        if (--pathCooldown <= 0) {
            unmason.getNavigation().startMovingTo(target.getX(), target.getY(), target.getZ(), SPEED);
            pathCooldown = 10; // re-path twice a second
        }
    }

    /** The player this Unmason should be following right now, or null. */
    private PlayerEntity candidate() {
        if (!(unmason.getWorld() instanceof ServerWorld sw)) return null;
        if (!StrongholdSpace.isInside(sw, unmason.getBlockPos())) return null;

        PlayerEntity player = UnmasonFreezeGoal.lastWatcherOf(unmason);
        if (player == null || !player.isAlive() || player.isSpectator()) return null;
        if (player.getWorld() != unmason.getWorld()) return null;
        if (player.squaredDistanceTo(unmason) > START_RANGE * START_RANGE) return null;
        if (!unmason.getVisibilityCache().canSee(player)) return null; // lost behind a corner
        return player;
    }
}
