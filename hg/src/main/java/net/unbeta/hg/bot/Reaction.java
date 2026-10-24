package net.unbeta.hg.bot;

import java.util.EnumSet;
import java.util.Set;

/**
 * One Reaction from the design doc's catalogue: a trigger read from the bot's senses and notebook,
 * a response its body plays out, a time limit, a tier and flavour tags. A Reaction class is a
 * template; the brain makes a fresh instance each time one fires (instances carry its progress).
 */
public abstract class Reaction {

    /** How urgent: a new trigger takes over only at the current Reaction's tier or above. */
    public enum Tier { ERRAND, OPPORTUNITY, NEED, COMBAT, SURVIVAL }

    /** Flavours, which the Win Methods read (and others can see). */
    public enum Tag { AGGRESSIVE, FEARFUL, GATHERING, CRAFTING, LOOTING, HIDING, SOCIAL }

    public enum Outcome { RUNNING, DONE, FAILED }

    /** "S1" */
    public final String id;
    /** "On fire" */
    public final String name;
    public final Tier tier;
    public final Set<Tag> tags;
    /** Base length in seconds; for "until done" Reactions, the most it may take. */
    public final double seconds;
    /**
     * A set length ("3 s"), which the Win Method stretches or shortens (Lower x1.75, Middling x0.5).
     * "Until done" Reactions end when their job does; their seconds are only a cap.
     */
    public final boolean fixed;

    /** Set by the brain when it starts: the tick it must stop by. */
    long endsAt;
    /** Why it fired, for /hg bot why. */
    public String because = "";
    /** Set off by something seen (Middling contagion): runs without its trigger. */
    boolean forced;

    protected Reaction(String id, String name, Tier tier, double seconds, boolean fixed, Tag... tags) {
        this.id = id;
        this.name = name;
        this.tier = tier;
        this.seconds = seconds;
        this.fixed = fixed;
        this.tags = tags.length == 0 ? EnumSet.noneOf(Tag.class) : EnumSet.of(tags[0], tags);
    }

    /** Does it fire now? Read only the brain's senses and notebook. Set {@link #because} when true. */
    public abstract boolean trigger(Brain brain);

    /** Plays one tick of the response. */
    public abstract Outcome tick(Brain brain);

    /** A fresh copy to run (its progress starts over). */
    public abstract Reaction fresh();

    /** Called when it stops for any reason (done, out of time, interrupted). */
    public void stopped(Brain brain) {}

    /** Mining, crafting, fighting: its eyes are busy, so its sight cone narrows. */
    public boolean eyesBusy() {
        return tags.contains(Tag.GATHERING) || tags.contains(Tag.CRAFTING) || tags.contains(Tag.AGGRESSIVE);
    }

    public String label() {
        return id + " " + name;
    }
}
