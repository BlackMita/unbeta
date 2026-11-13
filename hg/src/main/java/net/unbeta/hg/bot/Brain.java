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
    /** Ticks it has been swimming (no ground under it): long swims call for a boat. */
    int swimTicks;
    /** Ticks spent digging staircases and tunnels this round (there's a budget). */
    int excavated;
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

    /** Its watchdog: catches it looping, stuck, pacing or frozen, so it can snap out of it. */
    final Watch watch = new Watch();
    /** How many times running each Reaction has given up (each time it rests longer before trying again). */
    private final Map<String, Integer> failStreak = new HashMap<>();
    private final Map<String, Integer> loops = new HashMap<>();
    /** Places it tried to get at and couldn't: left alone until the tick given. */
    final Map<BlockPos, Long> spurned = new HashMap<>();

    /** Gives up on a place for 5 minutes. */
    void spurn(BlockPos pos) {
        long now = now();
        if (spurned.size() > 64) spurned.values().removeIf(t -> t < now);
        spurned.put(pos.toImmutable(), now + 6000);
    }

    /** The last tick its brain ran right through (the trial watches for brains that stop). */
    long lastThought;
    private int sleptFor;
    /** The last tick it finished making something (taking its table along waits a while after). */
    long lastCraftAt;
    /** When each job last finished well, the last few times (to catch it doing the same job over and over). */
    private final Map<String, java.util.ArrayDeque<Long>> doneAt = new HashMap<>();
    /** Jobs that are meant to come round again and again: chopping, planks, foraging, picking things up. */
    private static final java.util.Set<String> REPEATABLE = java.util.Set.of("N1", "N2", "N7", "O1");

    /** Its body found no way to anywhere from where it stands (set mid-walk, dealt with at the next tick). */
    boolean boxedIn;
    private long breakOutRest;
    /** A breath between one job and the next: until then it just stands and looks about. */
    private long settleUntil;
    /** Its errand broke: until then it does nothing clever. */
    private long errandRestUntil;
    /** Half-hearts above or below its Win Method's usual at which this one bot runs. */
    private final float nerve = (random.nextFloat() - 0.5F) * 3F;
    // Fidgeting (Errands.fidget)
    int fidgetNext, fidgetUntil, fidgetKind;
    float fidgetSide;

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
        if (bot.isSleeping()) {
            // In a bed: it gets up when day comes, or after half a minute (a night only passes if everyone sleeps).
            if (world().isDay() || bot.getSleepTimer() >= 100 && ++sleptFor > 600) {
                bot.wakeUp(false, true);
                sleptFor = 0;
            }
            return;
        }
        sleptFor = 0;
        lastThought = now;
        if (bot.isOnGround() && !bot.isInLava() && !bot.isTouchingWater()) lastSafe = bot.getBlockPos();
        if (bot.isOnGround()) fallRolled = false;
        swimTicks = bot.isTouchingWater() && !bot.isOnGround() && !bot.hasVehicle() ? swimTicks + 1 : 0;

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
            try {
                senses.look(current != null && current.eyesBusy());
                notebook.fade(now);
                for (Entity e : senses.visible()) if (isFoe(e)) lastFoeSeen = now;
                if (playing) think(now);
            } catch (Exception e) {
                Trial.error(this, "looking and thinking", e);
            }
        }
        if ((bot.age + bot.getId()) % Watch.EVERY == 0) watchdog(now, playing);
        if (boxedIn) {
            boxedIn = false;
            if (playing && now >= breakOutRest && !(current instanceof Errands.BreakOut) && !bot.hasVehicle() && !bot.isTouchingWater()) {
                breakOutRest = now + 600;
                Trial.note(this, Watch.Tell.STUCK, "boxed in (no way to anywhere from here) while " + doing() + ": getting itself out");
                record("WATCHDOG: boxed in - getting out");
                if (current != null) finish(current, Reaction.Outcome.FAILED, "watchdog: boxed in");
                queue.clear();
                watch.forget();
                settleUntil = 0;
                Reaction escape = new Errands.BreakOut();
                escape.because = "no way to anywhere from here";
                escape.forced = true;
                begin(escape, now);
            }
        }
        if (!playing && current != null) finish(current, Reaction.Outcome.DONE, "the round isn't on");

        body.quiet = true;
        try {
            if (current != null) {
                Reaction.Outcome o;
                boolean broke = false;
                if (now >= current.endsAt) {
                    o = null;
                } else {
                    try {
                        o = current.tick(this);
                    } catch (Exception e) {
                        Trial.error(this, "doing " + current.label(), e);
                        o = Reaction.Outcome.FAILED;
                        broke = true;
                    }
                }
                if (o == null) finish(current, Reaction.Outcome.DONE, "out of time");
                else if (broke) finish(current, o, "error (see the server log)");
                else if (o != Reaction.Outcome.RUNNING) finish(current, o, null);
            }
            boolean settling = playing && now < settleUntil;
            if (current == null && playing && !settling) {
                while (current == null && !queue.isEmpty()) {
                    Reaction next = queue.poll();
                    boolean fires;
                    try {
                        fires = next.forced || next.trigger(this);
                    } catch (Exception e) {
                        Trial.error(this, "checking " + next.label(), e);
                        fires = false;
                    }
                    if (fires) begin(next, now);
                }
            }
            if (current == null) {
                if (settling || now < errandRestUntil) {
                    body.pause();     // a breath between jobs: it stands and looks about
                } else {
                    try {
                        Reaction want = Errands.pick(this, phase, out, errand);
                        if (want != errand) {
                            if (errand != null) errand.stopped(this);
                            errand = want;
                        }
                        if (errand.tick(this) != Reaction.Outcome.RUNNING) {
                            errand.stopped(this);
                            errand = null;
                        }
                    } catch (Exception e) {
                        Trial.error(this, "on its errand" + (errand == null ? "" : " (" + errand.name + ")"), e);
                        errand = null;
                        errandRestUntil = now + 100;
                        body.stopMoving();
                    }
                }
            } else if (errand != null) {
                errand.stopped(this);
                errand = null;
            }
        } finally {
            body.quiet = false;
        }
        if (playing) lightReflexes();
        // Nothing pointed its eyes this tick: it looks about, as people do.
        if (!bot.hasVehicle() && body.gaze.commandedAge != bot.age
                && Math.abs(bot.forwardSpeed) < 0.05F && Math.abs(bot.sidewaysSpeed) < 0.05F) body.gaze.idle(this);
        waitingFidget(playing && watch.frozenFor() >= 60 && !body.wantsToMove() && bot.isOnGround() && !bot.hasVehicle());
        if (bot.age % 20 == 0) status();
    }

    private int waitNext, waitUntil;
    private boolean waitHeld;

    /**
     * Standing about with nothing changing for a few seconds (waiting on a furnace, watching
     * someone): now and then, at no set beat, it hops, ducks or swings its arm, on the spot.
     */
    private void waitingFidget(boolean waiting) {
        DummyPlayer bot = bot();
        int age = bot.age;
        if (waitHeld && (age >= waitUntil || !waiting)) {
            bot.setJumping(false);
            bot.setSneaking(false);
            waitHeld = false;
        }
        if (!waiting || waitHeld || age < waitNext) return;
        waitNext = age + (int) ((60 + random.nextInt(200)) * (1.5 - body.hands.restless));
        int kind = random.nextInt(3);
        if (kind == 0) {
            bot.setJumping(true);
            waitUntil = age + 2;
            waitHeld = true;
        } else if (kind == 1 && !bot.isSneaking()) {
            bot.setSneaking(true);
            waitUntil = age + 3 + random.nextInt(5);
            waitHeld = true;
        } else {
            bot.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        }
    }

    // ------------------------------------------------------------------ the watchdog

    /** A number that changes whenever it gets something done: its things, its health, hits landed, blocks broken. */
    private int signature() {
        DummyPlayer bot = bot();
        int h = 17;
        for (int i = 0; i < 36; i++) {
            net.minecraft.item.ItemStack st = bot.getInventory().getStack(i);
            if (!st.isEmpty()) h = h * 31 + System.identityHashCode(st.getItem()) + st.getCount() * 7 + st.getDamage() * 13 + i;
        }
        h = h * 31 + body.dug;
        h = h * 31 + (int) bot.getHealth();
        h = h * 31 + bot.getHungerManager().getFoodLevel();
        h = h * 31 + bot.totalExperience;
        for (int n : notebook.hits.values()) h += n * 101;
        return h;
    }

    /** Twice a second: is it stuck, pacing or frozen? If so it shakes itself loose. */
    private void watchdog(long now, boolean playing) {
        DummyPlayer bot = bot();
        BotConfig cfg = BotConfig.get();
        boolean crafting = current != null && current.tags.contains(Reaction.Tag.CRAFTING);
        boolean excused = !playing || bot.isSleeping() || bot.hasVehicle() || now - respawnedAt < 100
                || (current != null && current.tags.contains(Reaction.Tag.HIDING));
        Watch.Tell tell = watch.sample(bot.getX(), bot.getY(), bot.getZ(), body.wantsToMove(), signature(), excused,
                crafting ? cfg.frozenCraftingTicks : cfg.frozenTicks);
        if (tell == null) return;
        Trial.note(this, tell, "while " + doing() + (body.goal() == null ? "" : ", walking to " + body.goal().toShortString()));
        body.strikeOffGoal();
        shakeLoose(now, tell.word);
    }

    /** Drops whatever it's doing (resting it a while) and walks off a little way, to start afresh. */
    private void shakeLoose(long now, String why) {
        record("WATCHDOG: " + why + " - shaking loose");
        if (current != null) finish(current, Reaction.Outcome.FAILED, "watchdog: " + why);
        queue.clear();
        if (errand != null) {
            errand.stopped(this);
            errand = null;
        }
        watch.forget();
        settleUntil = 0;
        Reaction shake = new Errands.Shake();
        shake.because = why;
        shake.forced = true;
        begin(shake, now);
    }

    /** Mining, crafting, fighting: its eyes stay on the job (no glancing about). */
    boolean eyesBusy() {
        return current != null && current.eyesBusy();
    }

    /** Its body called a walk off after getting stuck or taking far too long. */
    void walkGivenUp(BlockPos goal, String why) {
        Trial.note(this, Watch.Tell.NO_WAY, why + " on the way to " + goal.toShortString() + " while " + doing());
        record("gave up walking to " + goal.toShortString() + ": " + why);
    }

    /**
     * Habits with light, no Reaction needed: in the dark it carries a lit torch or Jack o'Lantern
     * in its off hand (its main hand stays free for tools); and in water with a lit torch on it, it
     * keeps its head up, because going under puts every lit torch it carries out.
     */
    private void lightReflexes() {
        DummyPlayer bot = bot();
        boolean litTorch = body.has(Moves::litTorch) || Moves.litTorch(bot.getOffHandStack());
        if (litTorch && bot.isTouchingWater() && !bot.hasVehicle()) bot.setJumping(true);
        if (bot.age % 10 != 0) return;
        if (!bot.getOffHandStack().isEmpty()) return;
        if (world().getLightLevel(bot.getBlockPos()) > 7) return;
        // A Jack o'Lantern first near water (it's waterproof), else a torch.
        int slot = body.slot(st -> Moves.litLight(st) && !Moves.litTorch(st));
        if (slot < 0 || !bot.isTouchingWater()) {
            int torch = body.slot(Moves::litTorch);
            if (torch >= 0 && !bot.isTouchingWater()) slot = torch;
        }
        if (slot >= 0) body.toOffhand(slot);
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
            try {
                if (!trial.trigger(this)) continue;
            } catch (Exception e) {
                Trial.error(this, "checking " + r.label(), e);
                cooldown.put(r.id, now + 1200);
                continue;
            }
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
            // A breath between jobs: only something pressing cuts it short.
            if (now >= settleUntil || pick.tier.ordinal() > Reaction.Tier.NEED.ordinal()) begin(pick, now);
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
        // The same thing begun again and again from the same spot, with nothing to show for it: a loop.
        if (!(r instanceof Errands.Shake) && !(r instanceof Errands.BreakOut)
                && watch.began(r.id, now, bot().getX(), bot().getY(), bot().getZ(), signature())) {
            int n = loops.merge(r.id, 1, Integer::sum);
            long rest = r.tier == Reaction.Tier.SURVIVAL ? 100 : Math.min(4800L, 600L << Math.min(3, n - 1));
            cooldown.put(r.id, now + rest);
            Trial.note(this, Watch.Tell.LOOP, r.label() + " begun 4 times running here with nothing to show for it; resting it " + rest / 20 + " s");
            record("LOOP: resting " + r.label() + " for " + rest / 20 + "s");
            if (current != null) finish(current, Reaction.Outcome.DONE, "caught in a loop");
            Reaction shake = new Errands.Shake();
            shake.because = "kept starting " + r.label();
            shake.forced = true;
            r = shake;
        }
        r.startedAt = now;
        Trial.began(this, r);
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
        long cd;
        boolean gaveUp = how == Reaction.Outcome.FAILED && (why == null || why.startsWith("watchdog") || why.startsWith("error"));
        if (gaveUp) {
            // Each time running it gives up on the same thing, it leaves it longer: 15 s, 30 s, 1 min, 2 min.
            int streak = Math.min(4, failStreak.merge(r.id, 1, Integer::sum));
            cd = (long) cfg.failCooldownTicks << (streak - 1);
            if (r.tier == Reaction.Tier.SURVIVAL) cd = streak >= 3 || why != null ? 100 : 20;
        } else {
            cd = how == Reaction.Outcome.FAILED ? cfg.failCooldownTicks : cfg.doneCooldownTicks;
            if (how == Reaction.Outcome.DONE && why == null) failStreak.remove(r.id);
            if (r.tier == Reaction.Tier.SURVIVAL) cd = Math.min(cd, 20);
        }
        if (r.tags.contains(Reaction.Tag.CRAFTING)) lastCraftAt = now;
        // The same job finished a fourth time in four minutes: it's going round in circles. Rest it 3 min.
        if (how == Reaction.Outcome.DONE && why == null && !REPEATABLE.contains(r.id)
                && (r.tier == Reaction.Tier.NEED || r.tier == Reaction.Tier.OPPORTUNITY)) {
            java.util.ArrayDeque<Long> times = doneAt.computeIfAbsent(r.id, k -> new java.util.ArrayDeque<>());
            times.addLast(now);
            while (!times.isEmpty() && now - times.peekFirst() > 4800) times.pollFirst();
            if (times.size() >= 4) {
                times.clear();
                cd = Math.max(cd, 3600);
                Trial.note(this, Watch.Tell.REPEAT, r.label() + " done 4 times in 4 minutes; resting it 3 min");
                record("REPEATING: resting " + r.label() + " for 3 min");
            }
        }
        Long had = cooldown.get(r.id);
        if (had == null || had < now + cd) cooldown.put(r.id, now + cd);
        // A breath before the next job (not when something cut this one short).
        if (why == null && r.tier.ordinal() <= Reaction.Tier.NEED.ordinal() && now - r.startedAt >= 40
                && !(r instanceof Errands.Shake) && !(r instanceof Errands.BreakOut)) {
            settleUntil = now + body.hands.hesitate(random);
        }
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
        Trial.died(this);
        record("died: " + source.getName());
        if (Round.phase() == Round.Phase.GAME) chat(pickLine("rip", "ugh", "really?", "nooo"));
    }

    public void onRespawn() {
        respawnedAt = now();
        watch.forget();
        lastSafe = bot().getBlockPos();
        regeared = false;
        hogMark = null;
    }

    /** It killed something (Hearing passes this on). */
    void onKill(net.minecraft.entity.LivingEntity dead) {
        if (!(dead instanceof PlayerEntity)) return;
        lastKillAt = now();
        Trial.killed(this);
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
        double usual = switch (win()) {
            case LOWER -> cfg.lowerRetreatHealth;
            case MIDDLING -> cfg.middlingRetreatHealth;
            case HIGHER -> cfg.higherRetreatHealth;
        };
        return (float) Math.max(2.0, usual + nerve);
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
        return (int) Math.max(1, Math.round((lo + random.nextInt(hi - lo + 1)) / skill * body.hands.react));
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
        out.add(body.hands.title() + String.format(" (aim wanders %.1f deg, reacts x%.2f, footwork %.0f%%%s). Standing still %d%% of its free time.",
                body.hands.aimError, body.hands.react, body.hands.footwork * 100, body.hands.hopper ? ", a hopper" : "",
                Math.round(watch.stillShare() * 100)));
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
