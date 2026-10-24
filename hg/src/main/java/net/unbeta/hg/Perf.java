package net.unbeta.hg;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

/**
 * /hg perf: a cheap stopwatch. Times every server tick, and inside it, how long the dummies
 * and bots spend being players (vanilla's per-player work) and thinking (their bodies/brains).
 * Averages over the last 5 seconds (100 ticks).
 */
public final class Perf {

    private Perf() {}

    private static final int WINDOW = 100;
    private static long tickStart;
    private static long tickSum, playerSum, brainSum, worst;
    private static int ticks, bots;
    // Last finished window
    private static double avgTick, avgPlayer, avgBrain, worstTick;
    private static int lastBots;
    private static boolean ready;

    public static void register() {
        ServerTickEvents.START_SERVER_TICK.register(server -> tickStart = System.nanoTime());
        ServerTickEvents.END_SERVER_TICK.register(Perf::end);
    }

    /** Called by DummyPlayer each tick with how long each part took. */
    public static void dummy(long playerNanos, long brainNanos) {
        playerSum += playerNanos;
        brainSum += brainNanos;
        bots++;
    }

    private static void end(MinecraftServer server) {
        long t = System.nanoTime() - tickStart;
        tickSum += t;
        worst = Math.max(worst, t);
        if (++ticks < WINDOW) return;
        avgTick = tickSum / 1e6 / ticks;
        avgPlayer = playerSum / 1e6 / ticks;
        avgBrain = brainSum / 1e6 / ticks;
        worstTick = worst / 1e6;
        lastBots = bots / ticks;
        ready = true;
        tickSum = playerSum = brainSum = worst = 0;
        ticks = bots = 0;
    }

    public static String report() {
        if (!ready) return "Measuring... try again in 5 seconds.";
        double tps = Math.min(20.0, 1000.0 / Math.max(50.0, avgTick));
        String perBot = lastBots == 0 ? "" : String.format(" (%.2f ms each)", (avgPlayer + avgBrain) / lastBots);
        return String.format("Last 5 s: a tick takes %.1f ms on average (worst %.0f ms; 50 ms is the limit) -> about %.1f TPS.%n"
                        + "%d dummies/bots: %.1f ms being players + %.1f ms thinking per tick%s.%n"
                        + "Everything else (worlds, mobs, chunks, real players, other mods): %.1f ms.",
                avgTick, worstTick, tps, lastBots, avgPlayer, avgBrain, perBot, avgTick - avgPlayer - avgBrain);
    }
}
