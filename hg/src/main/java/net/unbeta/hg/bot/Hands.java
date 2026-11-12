package net.unbeta.hg.bot;

import java.util.Locale;
import java.util.Random;

/**
 * How good a bot is with its hands, and its little habits: rolled once when it joins, never
 * changed. Its traits decide what it wants and how it goes about it; its Hands decide how well it
 * pulls that off (aim, reactions, footwork, judging a fight). Two bots of the same grade still
 * differ a little, as two players of the same rank do. Nothing in here touches the game: it is
 * all numbers, so it can be tested on its own.
 */
public final class Hands {

    public enum Grade {
        ROOKIE("Rookie"), CASUAL("Casual"), STEADY("Steady"), SHARP("Sharp"), ACE("Ace");

        public final String title;

        Grade(String title) {
            this.title = title;
        }
    }

    //                                              Rookie Casual Steady Sharp  Ace
    private static final double[] AIM_ERROR =      {9.0,   6.0,   4.0,   2.5,   1.2};
    private static final double[] OVERSHOOT =      {0.30,  0.20,  0.12,  0.06,  0.02};
    private static final double[] TURN =           {0.70,  0.85,  1.00,  1.15,  1.35};
    private static final double[] REACT =          {1.60,  1.25,  1.00,  0.85,  0.70};
    private static final double[] MISS =           {1.60,  1.25,  1.00,  0.75,  0.50};
    private static final double[] JUDGEMENT =      {1.35,  1.15,  1.00,  0.97,  0.93};
    private static final double[] FOOTWORK =       {0.15,  0.40,  0.65,  0.85,  1.00};
    private static final double[] SPRINT_RESET =   {0.00,  0.05,  0.20,  0.50,  0.80};
    private static final int[] HESITATE_MIN =      {10,    8,     5,     3,     2};
    private static final int[] HESITATE_MAX =      {24,    18,    14,    9,     6};

    public final Grade grade;
    /** How far its aim wanders off a moving target, in degrees. */
    public final double aimError;
    /** How far past the mark a big flick of its head carries, as a share of the flick. */
    public final double overshoot;
    /** Head speed (1 = the tuning file's turn speed). */
    public final double turn;
    /** Reaction time (1 = normal; higher is slower). */
    public final double react;
    /** Mis-clicks (1 = normal; higher misses more). */
    public final double miss;
    /** How it rates its own chances in a fight: over 1 it thinks it's better than it is. */
    public final double judgement;
    /** How well it moves in a fight, 0 to 1. */
    public final double footwork;
    /** How often it lets go of forward after landing a hit, to hit harder with the next (0 to 1). */
    public final double sprintReset;
    private final int hesitateMin, hesitateMax;

    // Habits: nothing to do with skill.
    /** How much it looks about, shuffles and hops when it has nothing to do, 0 to 1. */
    public final double restless;
    /** Sprint-jumps across open ground. */
    public final boolean hopper;
    /** Circles a foe to the left more often than to the right (or the other way). */
    public final boolean leftLeaning;

    private Hands(Grade grade, Random r) {
        this.grade = grade;
        int g = grade.ordinal();
        this.aimError = vary(AIM_ERROR[g], r);
        this.overshoot = vary(OVERSHOOT[g], r);
        this.turn = vary(TURN[g], r);
        this.react = vary(REACT[g], r);
        this.miss = vary(MISS[g], r);
        this.judgement = vary(JUDGEMENT[g], r);
        this.footwork = Math.min(1.0, vary(FOOTWORK[g], r));
        this.sprintReset = Math.min(1.0, vary(SPRINT_RESET[g], r));
        this.hesitateMin = HESITATE_MIN[g];
        this.hesitateMax = HESITATE_MAX[g];
        this.restless = 0.2 + r.nextDouble() * 0.8;
        this.hopper = r.nextInt(100) < 25 + g * 10;
        this.leftLeaning = r.nextBoolean();
    }

    /** A number of its own: the grade's, give or take 15%. */
    private static double vary(double base, Random r) {
        return base * (0.85 + r.nextDouble() * 0.30);
    }

    /** The pause before it turns to its next job, in ticks. */
    public int hesitate(Random r) {
        return hesitateMin + r.nextInt(hesitateMax - hesitateMin + 1);
    }

    /** A grade named among some words ("ace", "2 sharp hare"), or null. */
    public static Grade parse(String words) {
        if (words == null) return null;
        for (String w : words.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
            for (Grade g : Grade.values()) if (g.title.toLowerCase(Locale.ROOT).equals(w)) return g;
        }
        return null;
    }

    /**
     * Hands for a new bot: the grade named in the words if there is one, else rolled by the shares
     * given (how many bots in a hundred are of each grade, Rookie first).
     */
    public static Hands roll(String words, int[] shares, Random r) {
        Grade g = parse(words);
        if (g == null) {
            int total = 0;
            for (int i = 0; i < Grade.values().length; i++) total += i < shares.length ? Math.max(0, shares[i]) : 0;
            g = Grade.STEADY;
            if (total > 0) {
                int pick = r.nextInt(total);
                for (int i = 0; i < Grade.values().length && i < shares.length; i++) {
                    pick -= Math.max(0, shares[i]);
                    if (pick < 0) {
                        g = Grade.values()[i];
                        break;
                    }
                }
            }
        }
        return new Hands(g, r);
    }

    /** "Sharp hands" */
    public String title() {
        return grade.title + " hands";
    }
}
