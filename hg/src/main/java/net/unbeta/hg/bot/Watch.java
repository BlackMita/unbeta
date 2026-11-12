package net.unbeta.hg.bot;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * The watchdog over one bot: it looks for the things that give a bot away as a bot, so the brain
 * can snap out of them (and the trial report can count them). It is fed a sample of the bot twice
 * a second, and told whenever a Reaction begins. Nothing in here touches the game: it is all
 * numbers, so it can be tested on its own.
 */
final class Watch {

    /** A bot-like thing it was caught doing. */
    enum Tell {
        ERROR("error", "something went wrong inside its head"),
        LOOP("loop", "started the same thing over and over with nothing to show for it"),
        STUCK("stuck", "kept trying to walk but went nowhere"),
        PACING("pacing", "walked back and forth over the same few blocks"),
        FROZEN("frozen", "stood doing nothing at all"),
        NO_WAY("no way there", "gave up on a walk it couldn't finish"),
        REPEAT("repeating", "finished the same job again and again (a fourth crafting table in four minutes, say)"),
        UNRESPONSIVE("unresponsive", "its brain stopped running for half a minute or more");

        final String word;
        final String meaning;

        Tell(String word, String meaning) {
            this.word = word;
            this.meaning = meaning;
        }
    }

    /** Ticks between samples. */
    static final int EVERY = 10;
    private static final int KEEP = 48;                 // 24 s of samples
    private static final int STUCK_SAMPLES = 16;        // 8 s
    private static final double STUCK_SPAN = 1.5;       // blocks
    private static final double PACING_SPAN = 5.0;
    private static final int LOOP_BEGINS = 4;
    private static final int LOOP_WINDOW = 1800;        // 90 s
    private static final double LOOP_SPAN = 3.0;

    private final double[] xs = new double[KEEP], ys = new double[KEEP], zs = new double[KEEP];
    private final boolean[] moving = new boolean[KEEP];
    private final int[] sigs = new int[KEEP];
    private int count, next;

    private double frozenX, frozenY, frozenZ;
    private int frozenSig;
    private int frozenTicks;
    private boolean frozenSet;

    private record Begin(long at, double x, double y, double z, int sig) {}
    private final Map<String, Deque<Begin>> begins = new HashMap<>();

    // Tallies for the report (still: of the samples where it had no good reason to be still)
    int samples, stillSamples, freeSamples;
    double walked;
    private double lastX, lastY, lastZ;
    private boolean hasLast;

    /**
     * One sample. wantsToMove: its legs are trying to take it somewhere. signature: a number that
     * changes whenever it gets something done (its things, its health, hits landed, blocks broken).
     * excused: it has a good reason to be still (asleep, hiding, not in the round).
     * frozenAfter: how many ticks of doing nothing at all count as frozen.
     * Returns what it was caught at, or null.
     */
    Tell sample(double x, double y, double z, boolean wantsToMove, int signature, boolean excused, int frozenAfter) {
        samples++;
        if (hasLast) {
            double step = Math.sqrt((x - lastX) * (x - lastX) + (z - lastZ) * (z - lastZ));
            if (!excused) {
                freeSamples++;
                if (step < 0.3 && Math.abs(y - lastY) < 0.3) stillSamples++;
            }
            if (step < 12) walked += step;       // a bigger jump is a respawn or a teleport
            else forget();
        }
        lastX = x;
        lastY = y;
        lastZ = z;
        hasLast = true;

        xs[next] = x;
        ys[next] = y;
        zs[next] = z;
        moving[next] = wantsToMove;
        sigs[next] = signature;
        next = (next + 1) % KEEP;
        if (count < KEEP) count++;

        // Frozen: hasn't moved, hasn't got anything done.
        if (excused) {
            frozenSet = false;
        } else if (!frozenSet || signature != frozenSig || dist(x, 0, z, frozenX, 0, frozenZ) > 1.0 || Math.abs(y - frozenY) > 2.0) {
            frozenSet = true;
            frozenX = x;
            frozenY = y;
            frozenZ = z;
            frozenSig = signature;
            frozenTicks = 0;
        } else {
            frozenTicks += EVERY;
            if (frozenTicks >= frozenAfter && !wantsToMove) {
                frozenSet = false;
                return Tell.FROZEN;
            }
        }
        if (excused) return null;

        // Stuck: legs going for most of 8 s, yet it has stayed within a block and a half and got nothing done
        // (toe to toe in a fight it gets hits in, or takes them: that isn't stuck).
        if (count >= STUCK_SAMPLES && share(STUCK_SAMPLES) >= 0.75 && span(STUCK_SAMPLES) < STUCK_SPAN
                && sameSignature(STUCK_SAMPLES)) {
            forget();
            return Tell.STUCK;
        }
        // Pacing: legs going for most of 24 s, within a few blocks, and nothing got done.
        if (count >= KEEP && share(KEEP) >= 0.75 && span(KEEP) < PACING_SPAN && sameSignature(KEEP)) {
            forget();
            return Tell.PACING;
        }
        return null;
    }

    /**
     * A Reaction began. True if that makes a loop: the fourth start of the same thing within a
     * minute and a half, each from the same spot, with nothing got done in between.
     */
    boolean began(String id, long now, double x, double y, double z, int signature) {
        Deque<Begin> list = begins.computeIfAbsent(id, k -> new ArrayDeque<>());
        list.addLast(new Begin(now, x, y, z, signature));
        while (list.size() > LOOP_BEGINS) list.pollFirst();
        if (list.size() < LOOP_BEGINS) return false;
        for (Begin b : list) {
            if (now - b.at() > LOOP_WINDOW || b.sig() != signature || dist(x, y, z, b.x(), b.y(), b.z()) > LOOP_SPAN) return false;
        }
        list.clear();
        return true;
    }

    /** How long (ticks) it has stood doing nothing at all. */
    int frozenFor() {
        return frozenSet ? frozenTicks : 0;
    }

    /** Start the walking watch over (it has just been shaken loose, or moved by something else). */
    void forget() {
        count = 0;
        next = 0;
        frozenSet = false;
    }

    /** The share of its free time (not asleep, hiding or out) it has spent not moving at all, 0 to 1. */
    double stillShare() {
        return freeSamples == 0 ? 0 : stillSamples / (double) freeSamples;
    }

    private static double dist(double x, double y, double z, double a, double b, double c) {
        return Math.sqrt((x - a) * (x - a) + (y - b) * (y - b) + (z - c) * (z - c));
    }

    private int index(int back) {
        return ((next - 1 - back) % KEEP + KEEP) % KEEP;
    }

    /** The share of the last n samples in which its legs were going. */
    private double share(int n) {
        int going = 0;
        for (int i = 0; i < n; i++) if (moving[index(i)]) going++;
        return going / (double) n;
    }

    /** How far apart the last n samples lie, corner to corner. */
    private double span(int n) {
        double x0 = Double.MAX_VALUE, y0 = Double.MAX_VALUE, z0 = Double.MAX_VALUE;
        double x1 = -Double.MAX_VALUE, y1 = -Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            int k = index(i);
            x0 = Math.min(x0, xs[k]);
            x1 = Math.max(x1, xs[k]);
            y0 = Math.min(y0, ys[k]);
            y1 = Math.max(y1, ys[k]);
            z0 = Math.min(z0, zs[k]);
            z1 = Math.max(z1, zs[k]);
        }
        return dist(x0, y0, z0, x1, y1, z1);
    }

    private boolean sameSignature(int n) {
        int first = sigs[index(0)];
        for (int i = 1; i < n; i++) if (sigs[index(i)] != first) return false;
        return true;
    }
}
