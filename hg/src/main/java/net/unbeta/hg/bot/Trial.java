package net.unbeta.hg.bot;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.unbeta.hg.Round;
import net.unbeta.hg.UnbetaHG;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The bots' trial: a count of every bot-like thing the watchdogs catch (errors, loops, getting
 * stuck, pacing, standing frozen, walks given up), who did it, when, where and while doing what.
 * It counts all the time. Every round is watched from its start and reported when it ends;
 * /hg bot trial [minutes] wipes the slate, watches for that long, then reports. A report is a few
 * lines in chat and the whole story in bot-trial.txt in the server folder.
 * /hg bot report shows the count so far.
 */
public final class Trial {

    private Trial() {}

    private static final int MOST_LINES = 120;

    private static final Map<String, int[]> tells = new LinkedHashMap<>();
    private static final Map<String, Integer> reactions = new LinkedHashMap<>();
    private static final Map<String, int[]> lives = new LinkedHashMap<>();      // deaths, kills, Reactions begun
    private static final List<String> lines = new ArrayList<>();
    private static final List<String> errors = new ArrayList<>();
    private static int dropped;
    private static long startedAt, endsAt = -1;
    private static int minutes;
    private static long ticks;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(Trial::tick);
    }

    private static Round.Phase lastPhase;

    private static void tick(MinecraftServer server) {
        ticks = server.getTicks();
        Round.Phase phase = Round.phase();
        if (phase != lastPhase) {
            // Every round is watched from its start, and reported when it ends.
            if (phase == Round.Phase.GRACE && !running()) reset(server);
            if (phase == Round.Phase.ENDED && lastPhase != null && !Bots.all(server).isEmpty()) {
                endsAt = -1;
                tellEveryone(server);
            }
            lastPhase = phase;
        }
        if (endsAt >= 0 && ticks >= endsAt) {
            endsAt = -1;
            tellEveryone(server);
        }
    }

    private static void tellEveryone(MinecraftServer server) {
        for (String line : summary(server)) {
            server.getPlayerManager().broadcast(Text.literal(line).formatted(Formatting.GRAY), false);
        }
        String where = write(server);
        server.getPlayerManager().broadcast(Text.literal(where).formatted(Formatting.GRAY), false);
    }

    /** Wipes the slate and watches for so many minutes. */
    public static void start(MinecraftServer server, int forMinutes) {
        reset(server);
        minutes = forMinutes;
        endsAt = ticks + forMinutes * 1200L;
    }

    private static void reset(MinecraftServer server) {
        tells.clear();
        reactions.clear();
        lives.clear();
        lines.clear();
        errors.clear();
        dropped = 0;
        for (Body b : Bots.all(server)) b.brain.watch.samples = b.brain.watch.stillSamples = 0;
        for (Body b : Bots.all(server)) b.brain.watch.walked = 0;
        ticks = server.getTicks();
        startedAt = ticks;
        minutes = 0;
    }

    public static boolean running() {
        return endsAt >= 0;
    }

    private static String clock() {
        long s = Math.max(0, ticks - startedAt) / 20;
        return String.format("%d:%02d", s / 60, s % 60);
    }

    /** A watchdog caught something. */
    static void note(Brain br, Watch.Tell tell, String detail) {
        String name = br.bot().getName().getString();
        tells.computeIfAbsent(name, k -> new int[Watch.Tell.values().length])[tell.ordinal()]++;
        String line = "[" + clock() + "] " + name + " " + tell.word.toUpperCase(java.util.Locale.ROOT) + " at "
                + br.bot().getBlockPos().toShortString() + ": " + detail;
        if (lines.size() < MOST_LINES) lines.add(line);
        else dropped++;
        UnbetaHG.LOG.warn("[bot trial] {}", line);
    }

    /** Something threw inside a bot's head: the first few lines of where, kept once each. */
    static void error(Brain br, String doing, Throwable t) {
        StringBuilder sb = new StringBuilder(doing + ": " + t);
        StackTraceElement[] trace = t.getStackTrace();
        for (int i = 0; i < Math.min(4, trace.length); i++) sb.append("\n      at ").append(trace[i]);
        String text = sb.toString();
        boolean fresh = !errors.contains(text);
        if (fresh && errors.size() < 20) errors.add(text);
        if (fresh) UnbetaHG.LOG.error("Bot {} failed while {}", br.bot().getName().getString(), doing, t);
        note(br, Watch.Tell.ERROR, doing + ": " + t);
    }

    static void began(Brain br, Reaction r) {
        reactions.merge(r.label(), 1, Integer::sum);
        life(br)[2]++;
    }

    static void died(Brain br) {
        life(br)[0]++;
    }

    static void killed(Brain br) {
        life(br)[1]++;
    }

    private static int[] life(Brain br) {
        return lives.computeIfAbsent(br.bot().getName().getString(), k -> new int[3]);
    }

    private static int total() {
        int n = 0;
        for (int[] t : tells.values()) for (int c : t) n += c;
        return n;
    }

    private static String counts(int[] t) {
        StringBuilder sb = new StringBuilder();
        for (Watch.Tell tell : Watch.Tell.values()) {
            if (t[tell.ordinal()] == 0) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(t[tell.ordinal()]).append(" x ").append(tell.word);
        }
        return sb.toString();
    }

    /** A few lines for chat. */
    public static List<String> summary(MinecraftServer server) {
        List<String> out = new ArrayList<>();
        int bots = Bots.all(server).size();
        long secs = Math.max(0, ticks - startedAt) / 20;
        out.add("Bot trial: " + bots + " bots watched for " + secs / 60 + " min " + secs % 60 + " s.");
        int n = total();
        if (n == 0) {
            out.add("Clean: nothing bot-like was caught.");
        } else {
            int[] all = new int[Watch.Tell.values().length];
            for (int[] t : tells.values()) for (int i = 0; i < all.length; i++) all[i] += t[i];
            out.add(n + " bot-like moments caught (each one snapped out of): " + counts(all) + ".");
            for (Map.Entry<String, int[]> e : tells.entrySet()) out.add("  " + e.getKey() + ": " + counts(e.getValue()));
        }
        return out;
    }

    /** Everything, for the file. */
    private static List<String> full(MinecraftServer server) {
        List<String> out = new ArrayList<>(summary(server));
        out.add("Round phase: " + Round.phase() + (minutes > 0 ? "   (trial length asked for: " + minutes + " min)" : ""));
        out.add("");
        out.add("What each word means:");
        for (Watch.Tell tell : Watch.Tell.values()) out.add("  " + tell.word + ": " + tell.meaning);
        out.add("");
        out.add("Each bot:");
        for (Body b : Bots.all(server)) {
            String name = b.bot.getName().getString();
            int[] l = lives.getOrDefault(name, new int[3]);
            int[] t = tells.get(name);
            out.add("  " + name + " (" + b.hands.title() + "; " + b.traits.title() + "): " + l[0] + " deaths, " + l[1] + " kills, "
                    + l[2] + " Reactions, walked " + Math.round(b.brain.watch.walked) + " blocks, standing still "
                    + Math.round(b.brain.watch.stillShare() * 100) + "% of the time; "
                    + (t == null ? "nothing caught" : counts(t)));
            out.add("      now: " + b.brain.doing());
        }
        out.add("");
        out.add("Caught, in order:" + (lines.isEmpty() ? " nothing" : ""));
        for (String line : lines) out.add("  " + line);
        if (dropped > 0) out.add("  ... and " + dropped + " more");
        if (!errors.isEmpty()) {
            out.add("");
            out.add("Errors (each kind once):");
            for (String e : errors) out.add("  " + e);
        }
        out.add("");
        out.add("Reactions run, most first:");
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(reactions.entrySet());
        sorted.sort((a, b) -> b.getValue() - a.getValue());
        for (int i = 0; i < Math.min(30, sorted.size()); i++) out.add("  " + sorted.get(i).getValue() + " x " + sorted.get(i).getKey());
        return out;
    }

    /** Writes the full report to bot-trial.txt in the server folder. Returns a line saying where (or why not). */
    public static String write(MinecraftServer server) {
        Path file = FabricLoader.getInstance().getGameDir().resolve("bot-trial.txt");
        try {
            Files.write(file, full(server));
            return "Full report written to " + file.toAbsolutePath();
        } catch (Exception e) {
            UnbetaHG.LOG.error("Couldn't write {}", file, e);
            return "Couldn't write the report file (see the server log).";
        }
    }
}
