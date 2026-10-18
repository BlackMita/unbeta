package net.unbeta.content.xpdrain;

import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.unbeta.content.obsidiandoor.XpHelper;

/**
 * Experience that visibly leaves a player and flies into something: an obsidian door being
 * paid, or a Guardian Post blasting it out of them. The points are real orbs, but "claimed":
 * no player can pick them up, they don't merge, they ignore walls, and after a short burst
 * outward they home in on their sink and vanish into it. A claimed orb whose sink is gone
 * (or that was saved with the world mid-flight) simply disappears.
 */
public final class XpDrain {

    /** Where claimed orbs go. */
    public interface Sink {
        Vec3d target();

        boolean alive();

        /** The orb arrived, carrying this many points. */
        void absorb(int points);
    }

    private XpDrain() {}

    /**
     * Takes up to {@code points} experience from the player and sends it flying into the sink as
     * a spray of orbs. Returns the points actually taken.
     *
     * @param burst      how hard the orbs scatter at first (blocks per tick)
     * @param burstTicks how long they scatter before homing in
     * @param speed      how fast they home in (blocks per tick)
     */
    public static int drain(ServerWorld world, PlayerEntity player, int points, Sink sink,
                            double burst, int burstTicks, double speed) {
        int taken = Math.min(points, XpHelper.totalPoints(player));
        if (taken <= 0) return 0;
        player.addExperience(-taken);
        Vec3d from = player.getPos().add(0, player.getHeight() * 0.6, 0);
        spray(world, from, taken, sink, burst, burstTicks, speed);
        return taken;
    }

    /** Sends {@code points} as a spray of claimed orbs from a point to the sink (no one is charged). */
    public static void spray(ServerWorld world, Vec3d from, int points, Sink sink,
                             double burst, int burstTicks, double speed) {
        int pieces = Math.max(1, Math.min(14, points / 4));
        int each = Math.max(1, points / pieces);
        int left = points;
        while (left > 0) {
            int value = left <= each ? left : each;
            left -= value;
            ExperienceOrbEntity orb = new ExperienceOrbEntity(world, from.x, from.y, from.z, value);
            double a = world.random.nextDouble() * Math.PI * 2;
            double up = (world.random.nextDouble() - 0.3) * burst;
            orb.setVelocity(Math.cos(a) * burst, up, Math.sin(a) * burst);
            ((XpOrbAccess) orb).unbeta$claim(sink, burstTicks + world.random.nextInt(3), speed);
            world.spawnEntity(orb);
        }
    }
}
