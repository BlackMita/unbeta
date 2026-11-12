package net.unbeta.hg.bot;

import java.util.Random;

/** The arithmetic of a human head and hand: plain numbers, so it can be tested without the game. */
final class Aim {

    private Aim() {}

    /** An angle brought into -180 to 180. */
    static float wrap(float degrees) {
        float d = degrees % 360F;
        if (d >= 180F) d -= 360F;
        if (d < -180F) d += 360F;
        return d;
    }

    /**
     * One tick of a head turning from one angle toward another: fast while it is far off, easing
     * in as it arrives (a person's flick, not a machine's steady sweep), and never faster than max.
     */
    static float step(float from, float to, float max, boolean wraps) {
        float rem = wraps ? wrap(to - from) : to - from;
        float size = Math.abs(rem);
        float step = Math.min(max, size * 0.5F + 2F);
        if (size <= step) return from + rem;
        return from + Math.signum(rem) * step;
    }

    /**
     * One tick of a hand's wander: a number that jitters about 0, always pulled back toward it,
     * with a spread of 1 over time. Multiply by degrees to get how far off the aim sits.
     */
    static double wander(double x, double pull, Random random) {
        return x * (1 - pull) + Math.sqrt(2 * pull - pull * pull) * random.nextGaussian();
    }

    /** The angle (degrees) between where it looks and where the thing is: both as yaw and pitch. */
    static double off(float yaw, float pitch, float toYaw, float toPitch) {
        double a = Math.toRadians(yaw), b = Math.toRadians(pitch), c = Math.toRadians(toYaw), d = Math.toRadians(toPitch);
        double dot = Math.cos(b) * Math.cos(d) * Math.cos(a - c) + Math.sin(b) * Math.sin(d);
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, dot))));
    }

    /** How far off (degrees) a swing may be and still land on something half a block wide at a distance. */
    static double tolerance(double distance) {
        return Math.toDegrees(Math.atan2(0.45, Math.max(0.5, distance))) + 4.0;
    }
}
