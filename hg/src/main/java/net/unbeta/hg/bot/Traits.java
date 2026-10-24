package net.unbeta.hg.bot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * A bot's three fixed traits (see the "Bot Brains" design doc): its Win Method (A), Keep Method
 * (B) and Player Method (C). Rolled once when the bot joins, never changed.
 */
public record Traits(Win win, Keep keep, Play play) {

    /** A: how Reactions are scheduled. */
    public enum Win {
        LOWER("Lower"), MIDDLING("Middling"), HIGHER("Higher");
        public final String title;
        Win(String title) { this.title = title; }
    }

    /** B: what the bot values and keeps. */
    public enum Keep {
        WOODSMAN("Woodsman"), FLETCHER("Fletcher"), IRONCLAD("Ironclad"), COPPERHEAD("Copperhead"),
        GILDED("Gilded"), SURVIVOR("Survivor"), LIGHTENER("Lightener"), MEDIC("Medic"), GLUTTON("Glutton"),
        BULWARK("Bulwark"), DUELIST("Duelist"), BUILDER("Builder"), HOMESTEADER("Homesteader"),
        BUCKETEER("Bucketeer"), SPELUNKER("Spelunker"), PEARL_DIVER("Pearl Diver"), PACK_RAT("Pack Rat"),
        COPYCAT("Copycat"), GAMBLER("Gambler"), BOMBER("Bomber"), BANKER("Banker");
        public final String title;
        Keep(String title) { this.title = title; }
    }

    /** C: what the bot does with its free time, and how it treats players. */
    public enum Play {
        HARE("Hare"), HOG("Hog"), TORTOISE("Tortoise");
        public final String title;
        Play(String title) { this.title = title; }
    }

    /** "the Middling Survivor Hog" */
    public String title() {
        return "the " + win.title + " " + keep.title + " " + play.title;
    }

    private static String squash(String s) {
        return s.toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "");
    }

    /**
     * Traits from words such as "lower lightener hare" (any order, any missing ones rolled at
     * random), never repeating a mix already in use. Null if every mix is taken.
     */
    public static Traits roll(String words, Set<Traits> taken, Random random) {
        Win w = null;
        Keep k = null;
        Play p = null;
        if (words != null) {
            String[] parts = words.trim().split("\\s+");
            for (int i = 0; i < parts.length; i++) {
                String one = squash(parts[i]);
                String two = i + 1 < parts.length ? one + squash(parts[i + 1]) : "";
                for (Win x : Win.values()) if (squash(x.title).equals(one)) w = x;
                for (Play x : Play.values()) if (squash(x.title).equals(one)) p = x;
                for (Keep x : Keep.values()) {
                    if (squash(x.title).equals(one)) k = x;
                    else if (squash(x.title).equals(two)) { k = x; i++; }
                }
            }
        }
        List<Traits> options = new ArrayList<>();
        for (Win a : Win.values()) for (Keep b : Keep.values()) for (Play c : Play.values()) {
            if (w != null && a != w) continue;
            if (k != null && b != k) continue;
            if (p != null && c != p) continue;
            Traits t = new Traits(a, b, c);
            if (!taken.contains(t)) options.add(t);
        }
        return options.isEmpty() ? null : options.get(random.nextInt(options.size()));
    }
}
