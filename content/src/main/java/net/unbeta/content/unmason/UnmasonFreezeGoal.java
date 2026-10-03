package net.unbeta.content.unmason;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.unbeta.content.stronghold.StrongholdSpace;

import java.util.EnumSet;

/**
 * Inside the stronghold, an Unmason holds still while a nearby player is looking at it -
 * guardians of the place posing as part of the masonry, moving only when unobserved.
 *
 * <p>It only stops moving: Unmasons have no attack goals in the first place, and damage and
 * knockback are physics, so both still land normally.
 *
 * <p>The Unmason must be inside the stronghold (StrongholdSpace, piece-level); where the
 * player stands doesn't matter, so you can freeze one by looking in from outside, and one
 * that has wandered out into the caves can't be frozen at all.
 *
 * <p>Priority 0 - above wandering and stronghold-seeking, so holding still wins over both.
 * A repair mission will sit above this in turn, so staring can never stall a repair.
 */
public class UnmasonFreezeGoal extends Goal {

    private static final double WATCH_RANGE = 8.0;
    /** cos(80 degrees): the Unmason is within the player's 160-degree forward cone - the creeper's test. */
    private static final double LOOK_CONE_COS = Math.cos(Math.toRadians(80.0));

    /** Unmason uuid -> the player it last saw looking at it. Cleared when that player leaves. */
    private static final java.util.Map<java.util.UUID, PlayerEntity> LAST_WATCHER =
            new java.util.concurrent.ConcurrentHashMap<>();

    private final UnmasonEntity unmason;

    public UnmasonFreezeGoal(UnmasonEntity unmason) {
        this.unmason = unmason;
        this.setControls(EnumSet.of(Control.MOVE, Control.JUMP));
    }

    @Override
    public boolean canStart() {
        return watcher() != null;
    }

    /**
     * The player this Unmason last saw looking at it, or null. UnmasonFollowGoal uses this:
     * a glance is what marks a player, and the Unmason follows that one afterwards.
     */
    public static PlayerEntity lastWatcherOf(UnmasonEntity unmason) {
        return LAST_WATCHER.get(unmason.getUuid());
    }

    @Override
    public boolean shouldContinue() {
        return watcher() != null;
    }

    @Override
    public void start() {
        unmason.getNavigation().stop();
    }

    @Override
    public void tick() {
        unmason.getNavigation().stop();
    }

    /** A player within range who can see this Unmason and is looking at it, or null. */
    private PlayerEntity watcher() {
        if (!(unmason.getWorld() instanceof ServerWorld sw)) return null;
        if (!StrongholdSpace.isInside(sw, unmason.getBlockPos())) return null;

        for (PlayerEntity player : sw.getPlayers()) {
            if (player.isSpectator()) continue;
            if (player.squaredDistanceTo(unmason) > WATCH_RANGE * WATCH_RANGE) continue;
            // The cache vanilla's own targeting goals use: one ray per tick, not per call.
            if (!unmason.getVisibilityCache().canSee(player)) continue; // no freezing through walls

            Vec3d look = player.getRotationVec(1.0F).normalize();
            Vec3d toUnmason = unmason.getPos().subtract(player.getEyePos());
            if (toUnmason.lengthSquared() < 0.01) { remember(player); return player; }
            if (look.dotProduct(toUnmason.normalize()) > LOOK_CONE_COS) {
                remember(player);
                return player;
            }
        }
        return null;
    }

    private void remember(PlayerEntity player) {
        LAST_WATCHER.put(unmason.getUuid(), player);
    }
}
