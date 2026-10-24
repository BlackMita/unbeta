package net.unbeta.hg.bot;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.unbeta.hg.DummyPlayer;
import net.unbeta.hg.Round;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The Reaction engine (design doc, D): a few times a second the bot looks, then checks every
 * Reaction's trigger. The one that fires becomes its single current Reaction, unless something at
 * a lower tier is asking; with nothing firing, its Errand runs. The Win Method (A) bends the
 * rules: Lower takes over at once and can fly into a rage, Middling queues and copies what it
 * sees others do, Higher takes over at once but tires of repeating itself.
 */
public final class Brain {

    public final Body body;
    public final Notebook notebook = new Notebook();
    public final Senses senses;
    final Random random = new Random();

    private Reaction current;
    private final Deque<Reaction> queue = new ArrayDeque<>();
    private Reaction errand;
    /** Triggers that were true at the last think (a same-tier takeover needs a trigger that's newly true). */
    private Set<String> wasTrue = new HashSet<>();
    private final Map<String, Long> cooldown = new HashMap<>();
    /** Higher: Reactions it has tired of, until when. */
    private final Map<String, Long> ignored = new HashMap<>();
    private String lastStarted;
    private int startedRunning;
    /** Lower: recent interruptions (when, and what was interrupted). */
    private record Interruption(long at, Reaction what) {}
    private final Deque<Interruption> interruptions = new ArrayDeque<>();
    private String rageId;
    private long rageUntil;
    private long nextContagion;
    private boolean enabled = true;

    // Player Methods
    /** Hare: who it's stalking. */
    java.util.UUID quarry;
    /** Hog: the chokepoint it's standing at. */
    BlockPos hogMark;
    /** Tortoise: has it fetched its stash since it last died? */
    boolean regeared = true;
    long handledC2 = -1;
    long lastKillAt = -1000;
    private final Deque<Hearing.Sound> sounds = new ArrayDeque<>();
    /** Times monsters have killed it at night (from then on it hides at night). */
    int nightDeaths;
    private long rolledNight = -1;
    private boolean timidTonight;
    private boolean hadIron;

    /** Picking up the drops of the block it just broke. */
    BlockPos collectPos;
    int collectTicks;

    // Things its triggers want to know about itself
    BlockPos lastSafe;
    long respawnedAt = -10000;
    long lastFoeSeen = -10000;
    long handledHurt = -1;
    boolean fallRolled;
    boolean fallSave;

    private final List<String> history = new ArrayList<>();
    private String lastStatus = "";

    Brain(Body body) {
        this.body = body;
        this.senses = new Senses(this);
    }

    public DummyPlayer bot() {
        return body.bot;
    }

    public ServerWorld world() {
        return body.world();
    }

    public long now() {
        return world().getTime();
    }

    public Traits traits() {
        return body.traits;
    }

    public Traits.Win win() {
        return body.traits.win();
    }

    // ------------------------------------------------------------------ the loop

    void tick() {
        DummyPlayer bot = bot();
        long now = now();
        if (!enabled || !Bots.brainsOn) {
            body.stopMoving();
            return;
        }
        if (bot.isSleeping()) return;
        if (bot.isOnGround() && !bot.isInLava() && !bot.isTouchingWater()) lastSafe = bot.getBlockPos();
        if (bot.isOnGround()) fallRolled = false;

        Round.Phase phase = Round.phase();
        boolean out = Round.isOut(bot);
        boolean playing = !out && (phase == Round.Phase.GRACE || phase == Round.Phase.GAME);

        BotConfig cfg = BotConfig.get();
        body.noSprint = traits().play() == Traits.Play.TORTOISE && !terminator() && phase != Round.Phase.LOBBY;
        if ((bot.age + bot.getId()) % 100 == 0 && !out) {
            Keeping.tickBuying(this);
            flavourNews();
        }
        boolean thinkTick = (bot.age + bot.getId()) % cfg.thinkTicks == 0;
        if (thinkTick) {
            senses.look(current != null && current.eyesBusy());
            notebook.fade(now);
            for (Entity e : senses.visible()) if (isFoe(e)) lastFoeSeen = now;
            if (playing) think(now);
        }
        if (!playing && current != null) finish(current, Reaction.Outcome.DONE, "the round isn't on");

        body.quiet = true;
        try {
            if (current != null) {
                Reaction.Outcome o;
                if (now >= current.endsAt) o = null;
                else o = current.tick(this);
                if (o == null) finish(current, Reaction.Outcome.DONE, "out of time");
                else if (o != Reaction.Outcome.RUNNING) finish(current, o, null);
            }
            if (current == null && playing) {
                while (current == null && !queue.isEmpty()) {
                    Reaction next = queue.poll();
                    if (next.forced || next.trigger(this)) begin(next, now);
                }
            }
            if (current == null) {
                Reaction want = Errands.pick(this, phase, out, errand);
                if (want != errand) {
                    if (errand != null) errand.stopped(this);
                    errand = want;
                }
                if (errand.tick(this) != Reaction.Outcome.RUNNING) {
                    errand.stopped(this);
                    errand = null;
                }
            } else if (errand != null) {
                errand.stopped(this);
                errand = null;
            }
        } finally {
            body.quiet = false;
        }
        if (bot.age % 20 == 0) status();
    }

    /** Checks every trigger, highest tier first, and decides what happens. */
    private void think(long now) {
        Set<String> nowTrue = new HashSet<>();
        Reaction pick = null;
        boolean raging = rageId != null && now < rageUntil;
        if (!raging) rageId = null;
        for (Reaction r : Catalogue.ALL) {
            if (raging && !r.id.equals(rageId)) continue;
            Long cd = cooldown.get(r.id);
            if (cd != null && now < cd) continue;
            Long ign = ignored.get(r.id);
            if (ign != null && now < ign && r.tier != Reaction.Tier.SURVIVAL) continue;
            if (current != null && current.id.equals(r.id)) {
                nowTrue.add(r.id);
                continue;
            }
            if (current != null && r.tier.ordinal() < current.tier.ordinal()) continue;
            if (r.tier == Reaction.Tier.COMBAT && Round.phase() != Round.Phase.GAME) continue;   // nobody can be hurt yet
            Reaction trial = r.fresh();
            if (!trial.trigger(this)) continue;
            nowTrue.add(r.id);
            if (pick == null) pick = trial;
        }
        Set<String> before = wasTrue;
        wasTrue = nowTrue;
        if (pick == null) {
            if (win() == Traits.Win.MIDDLING) contagion(now);
            return;
        }
        if (current == null) {
            begin(pick, now);
        } else if (pick.tier.ordinal() > current.tier.ordinal()) {
            interrupted(current, now);
            finish(current, Reaction.Outcome.FAILED, "interrupted by " + pick.label());
            begin(pick, now);
        } else if (!before.contains(pick.id)) {
            // Same tier, newly true.
            if (win() == Traits.Win.MIDDLING && pick.tier != Reaction.Tier.SURVIVAL) {
                enqueue(pick);
            } else {
                finish(current, Reaction.Outcome.FAILED, "dropped for " + pick.label());
                begin(pick, now);
            }
        }
        if (win() == Traits.Win.MIDDLING) contagion(now);
    }

    private void enqueue(Reaction r) {
        if (queue.size() >= BotConfig.get().middlingQueue) return;
        for (Reaction q : queue) if (q.id.equals(r.id)) return;
        queue.add(r);
    }

    private void begin(Reaction r, long now) {
        BotConfig cfg = BotConfig.get();
        double mult = switch (win()) {
            case LOWER -> cfg.lowerLength;
            case MIDDLING -> cfg.middlingLength;
            case HIGHER -> cfg.higherLength;
        };
        double secs = r.fixed ? r.seconds * mult : r.seconds;
        r.endsAt = now + Math.max(10, Math.round(secs * 20));
        current = r;
        body.stopMoving();
        body.stopBreaking();
        record("began " + r.label() + (r.because.isEmpty() ? "" : " (" + r.because + ")"));

        // Higher: the same Reaction three times running, and it stops caring for a minute.
        if (r.tier != Reaction.Tier.SURVIVAL) {
            if (r.id.equals(lastStarted)) startedRunning++;
            else startedRunning = 1;
            lastStarted = r.id;
            if (win() == Traits.Win.HIGHER && startedRunning >= 3) {
                ignored.put(r.id, now + cfg.wearsOffTicks);
                startedRunning = 0;
                record("tired of " + r.label() + " for " + cfg.wearsOffTicks / 20 + "s");
            }
        }
    }

    private void finish(Reaction r, Reaction.Outcome how, String why) {
        r.stopped(this);
        body.stopMoving();
        body.stopBreaking();
        bot().setSneaking(false);
        long now = now();
        String note = why != null ? why : how == Reaction.Outcome.FAILED ? "gave up" + (body.lastSaid.isEmpty() ? "" : ": " + body.lastSaid) : "done";
        record("ended " + r.label() + ": " + note);
        body.lastSaid = "";
        BotConfig cfg = BotConfig.get();
        long cd = how == Reaction.Outcome.FAILED ? cfg.failCooldownTicks : cfg.doneCooldownTicks;
        if (r.tier == Reaction.Tier.SURVIVAL) cd = Math.min(cd, 20);
        cooldown.put(r.id, now + cd);
        if (current == r) current = null;
    }

    /** Lower: pulled away three times in 20 s, and it locks on for 60 s, deaf to everything else. */
    private void interrupted(Reaction r, long now) {
        if (win() != Traits.Win.LOWER) return;
        BotConfig cfg = BotConfig.get();
        interruptions.add(new Interruption(now, r));
        while (!interruptions.isEmpty() && now - interruptions.peek().at() > cfg.rageWindowTicks) interruptions.poll();
        if (interruptions.size() < 3) return;
        Reaction lock = null;
        for (Interruption i : interruptions) {
            if (i.what().tags.contains(Reaction.Tag.AGGRESSIVE)) lock = i.what();
        }
        if (lock == null) lock = interruptions.peekLast().what();
        interruptions.clear();
        rageId = lock.id;
        rageUntil = now + cfg.rageTicks;
        record("RAGE: locked onto " + lock.label() + " for " + cfg.rageTicks / 20 + "s");
    }

    /** Middling: every 2 s, each person nearby showing a flavour may set it off doing the same. */
    private void contagion(long now) {
        BotConfig cfg = BotConfig.get();
        if (now < nextContagion) return;
        nextContagion = now + cfg.contagionTicks;
        for (Notebook.Sighting s : notebook.recent(now)) {
            if (s.showing == null || now - s.seen > cfg.contagionTicks || s.pos.distanceTo(bot().getPos()) > 16) continue;
            if (random.nextDouble() >= cfg.contagionChance) continue;
            Reaction copy = Catalogue.copy(s, this);
            if (copy == null) continue;
            copy.because = "copying " + s.name;
            if (current == null) begin(copy, now);
            else enqueue(copy);
        }
    }

    // ------------------------------------------------------------------ events

    /** An order from /hg bot takes over. */
    void standAside() {
        if (current != null) finish(current, Reaction.Outcome.DONE, "given an order");
        queue.clear();
        if (errand != null) {
            errand.stopped(this);
            errand = null;
        }
    }

    public void onDeath(DamageSource source) {
        notebook.note(Notebook.Kind.DEATH, bot().getBlockPos(), now(), "where I died", bot().getBlockPos());
        if (source.getAttacker() instanceof PlayerEntity killer && killer != bot()) {
            notebook.grudge = killer.getUuid();
            notebook.grudgeName = killer.getName().getString();
        }
        if (world().isNight() && source.getAttacker() instanceof Monster) nightDeaths++;
        if (current != null) finish(current, Reaction.Outcome.FAILED, "died");
        queue.clear();
        record("died: " + source.getName());
        if (Round.phase() == Round.Phase.GAME) chat(pickLine("rip", "ugh", "really?", "nooo"));
    }

    public void onRespawn() {
        respawnedAt = now();
        lastSafe = bot().getBlockPos();
        regeared = false;
        hogMark = null;
    }

    /** It killed something (Hearing passes this on). */
    void onKill(net.minecraft.entity.LivingEntity dead) {
        if (!(dead instanceof PlayerEntity)) return;
        lastKillAt = now();
        record("killed " + dead.getName().getString());
        chat(pickLine("gg", "ez", "got em", "rip"));
    }

    void hear(Hearing.Sound s) {
        sounds.add(s);
        while (sounds.size() > 8) sounds.poll();
    }

    Hearing.Sound lastSound() {
        return sounds.peekLast();
    }

    void forgetSound() {
        sounds.clear();
    }

    /** N16: does it hide tonight? Rolled once per night; always, once monsters have killed it at night. */
    boolean hidesTonight() {
        if (nightDeaths > 0) return true;
        long night = world().getTimeOfDay() / 24000;
        if (night != rolledNight) {
            rolledNight = night;
            BotConfig c = BotConfig.get();
            double chance = switch (win()) {
                case LOWER -> c.lowerHideChance;
                case MIDDLING -> c.middlingHideChance;
                case HIGHER -> c.higherHideChance;
            };
            if (!body.has(Moves::weapon)) chance *= 2;
            timidTonight = random.nextDouble() < chance;
        }
        return timidTonight;
    }

    /** Tortoise: late in the round (15 minutes left, or over half the players out), it starts hunting. */
    public boolean terminator() {
        if (Round.phase() != Round.Phase.GAME) return false;
        return Round.ticksLeft() <= BotConfig.get().terminatorTicksLeft
                || Round.stillIn().size() * 2 < Round.startedWith();
    }

    // ------------------------------------------------------------------ F2: chat lines (rare)

    /** Says something in chat like a player would, about one time in four. */
    void chat(String line) {
        if (random.nextDouble() >= BotConfig.get().chatChance) return;
        bot().getServer().getPlayerManager().broadcast(
                net.minecraft.text.Text.literal("<" + bot().getName().getString() + "> " + line), false);
    }

    String pickLine(String... lines) {
        return lines[random.nextInt(lines.length)];
    }

    /** News worth a word: its first iron. */
    private void flavourNews() {
        boolean iron = body.has(st -> st.isOf(net.minecraft.item.Items.RAW_IRON) || st.isOf(net.minecraft.item.Items.IRON_INGOT));
        if (iron && !hadIron) chat(pickLine("iron!", "got iron", "IRON"));
        hadIron = iron;
    }

    public void setEnabled(boolean on) {
        enabled = on;
        if (!on) standAside();
    }

    public boolean enabled() {
        return enabled;
    }

    // ------------------------------------------------------------------ shared judgements

    /** Someone it should be wary of: a hostile mob or another player still in the round. */
    public boolean isFoe(Entity e) {
        if (e == bot() || !(e instanceof LivingEntity le) || le.isDead()) return false;
        if (e instanceof PlayerEntity p) return !p.isSpectator() && !p.isCreative() && !Round.isOut(p);
        return e instanceof Monster;
    }

    /** The nearest foe it can see within range, or null. */
    public LivingEntity nearestFoe(double range) {
        LivingEntity best = null;
        double bestD = range * range;
        for (Entity e : senses.visible()) {
            if (!isFoe(e)) continue;
            double d = e.squaredDistanceTo(bot());
            if (d < bestD) {
                bestD = d;
                best = (LivingEntity) e;
            }
        }
        return best;
    }

    /** Health at which it retreats (Lower 3 hearts, Middling 5, Higher 4 until fight scores arrive). */
    public float retreatHealth() {
        BotConfig cfg = BotConfig.get();
        return (float) switch (win()) {
            case LOWER -> cfg.lowerRetreatHealth;
            case MIDDLING -> cfg.middlingRetreatHealth;
            case HIGHER -> cfg.higherRetreatHealth;
        };
    }

    /** Its reaction delay for something newly seen, in ticks (by Win Method, sharper with skill). */
    int reactionDelay() {
        int lo, hi;
        switch (win()) {
            case LOWER -> { lo = 3; hi = 5; }
            case MIDDLING -> { lo = 5; hi = 8; }
            default -> { lo = 4; hi = 7; }
        }
        double skill = Math.max(0.25, BotConfig.get().skill);
        return (int) Math.round((lo + random.nextInt(hi - lo + 1)) / skill);
    }

    public Vec3d spawn() {
        return Vec3d.ofBottomCenter(world().getSpawnPos());
    }

    // ------------------------------------------------------------------ what it shows

    private void record(String line) {
        long t = now() / 20;
        history.add(String.format("[%d:%02d] %s", (t / 60) % 1000, t % 60, line));
        while (history.size() > 20) history.remove(0);
    }

    /** "S1 On fire 3s" for the nametag, or the errand. */
    public String doing() {
        if (!enabled || !Bots.brainsOn) return "brain off";
        if (body.hasOrder()) return "order: " + body.doing();
        if (current != null) {
            long left = Math.max(0, (current.endsAt - now()) / 20);
            return current.label() + " " + left + "s";
        }
        return errand == null ? "idle" : errand.label();
    }

    private void status() {
        String s = doing();
        if (s.equals(lastStatus)) return;
        lastStatus = s;
        Bots.status(bot(), s);
    }

    /** Lines for /hg bot why. */
    public List<String> why() {
        List<String> out = new ArrayList<>();
        long now = now();
        out.add("Now: " + doing() + (current != null && !current.because.isEmpty() ? " - because " + current.because : ""));
        if (!queue.isEmpty()) {
            StringBuilder q = new StringBuilder("Queued:");
            for (Reaction r : queue) q.append(' ').append(r.label()).append(';');
            out.add(q.toString());
        }
        if (rageId != null && now < rageUntil) out.add("RAGE on " + rageId + ", " + (rageUntil - now) / 20 + "s left");
        for (Map.Entry<String, Long> e : ignored.entrySet()) {
            if (now < e.getValue()) out.add("Tired of " + e.getKey() + " for " + (e.getValue() - now) / 20 + "s more");
        }
        int from = Math.max(0, history.size() - 10);
        out.add("Last " + (history.size() - from) + ":");
        for (int i = from; i < history.size(); i++) out.add("  " + history.get(i));
        return out;
    }
}
