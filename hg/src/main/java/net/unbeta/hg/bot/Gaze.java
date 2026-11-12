package net.unbeta.hg.bot;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.unbeta.hg.DummyPlayer;

import java.util.Random;

/**
 * A bot's head. Everything that points its eyes goes through here, so it always turns as a person
 * does: quick then easing in, a touch past the mark on a big flick, never snapping. Aiming at
 * something that moves, its aim wanders by its Hands. With nothing to look at it still looks
 * about: at whoever is near, toward a noise, down at its hands, off to one side.
 */
final class Gaze {

    private final Body body;
    private final Random random = new Random();

    /** Where its aim sits off true, as a wander of spread 1 (times its Hands' aim error). */
    private double driftYaw, driftPitch;
    /** What is left of a flick's overshoot, in degrees. */
    private double over;
    private float lastYaw;
    private boolean had;
    /** The bot's age when something last told its head where to look (so idling knows to keep out). */
    int commandedAge = -1;

    Gaze(Body body) {
        this.body = body;
    }

    static float yawTo(Vec3d from, Vec3d to) {
        return (float) (MathHelper.atan2(to.z - from.z, to.x - from.x) * (180.0 / Math.PI)) - 90.0F;
    }

    static float pitchTo(Vec3d from, Vec3d to) {
        double dx = to.x - from.x, dz = to.z - from.z;
        return (float) -(MathHelper.atan2(to.y - from.y, Math.sqrt(dx * dx + dz * dz)) * (180.0 / Math.PI));
    }

    /** Turns toward a point with steady hands. True once it faces it. */
    boolean look(Vec3d point) {
        Vec3d eye = body.bot.getEyePos();
        return turnTo(yawTo(eye, point), pitchTo(eye, point), 0, 1.0, true);
    }

    /** Aims at something alive: its aim wanders by its Hands, more when the target is moving. */
    boolean aim(Entity target) {
        Vec3d eye = body.bot.getEyePos();
        Vec3d at = target.getEyePos().add(0, -0.3, 0);
        double speed = target.getVelocity().horizontalLength();
        double share = 0.4 + Math.min(0.6, speed * 3.0);
        return turnTo(yawTo(eye, at), pitchTo(eye, at), body.hands.aimError * share, 1.0, true);
    }

    /** How far off (degrees) its eyes are from something right now. */
    double offFrom(Entity target) {
        DummyPlayer bot = body.bot;
        Vec3d eye = bot.getEyePos();
        Vec3d at = target.getPos().add(0, target.getHeight() * 0.6, 0);
        return Aim.off(bot.getYaw(), bot.getPitch(), yawTo(eye, at), pitchTo(eye, at));
    }

    /**
     * Turns its head toward a yaw and pitch. error: degrees its aim wanders; speed: share of its
     * full head speed; flicks: whether a sudden new target makes it overshoot.
     */
    boolean turnTo(float yaw, float pitch, double error, double speed, boolean flicks) {
        DummyPlayer bot = body.bot;
        BotConfig cfg = BotConfig.get();
        Hands h = body.hands;
        double skill = Math.max(0.25, cfg.skill);
        commandedAge = bot.age;
        if (flicks && had && Math.abs(Aim.wrap(yaw - lastYaw)) > 30F) {
            // A new thing to look at, well away from the last: the flick carries a little past it.
            over = Aim.wrap(yaw - bot.getYaw()) * h.overshoot / skill * (0.5 + random.nextDouble());
        }
        lastYaw = yaw;
        had = true;
        double wantYaw = yaw + over, wantPitch = pitch;
        if (error > 0) {
            driftYaw = Aim.wander(driftYaw, 0.08, random);
            driftPitch = Aim.wander(driftPitch, 0.08, random);
            wantYaw += driftYaw * error / skill;
            wantPitch += driftPitch * error / skill * 0.6;
        }
        float max = (float) Math.max(2.0, cfg.turn() * h.turn * speed);
        float newYaw = Aim.step(bot.getYaw(), (float) wantYaw, max, true);
        float newPitch = MathHelper.clamp(Aim.step(bot.getPitch(), (float) wantPitch, max, false), -90F, 90F);
        if (over != 0 && Math.abs(Aim.wrap((float) wantYaw - newYaw)) < 3F) {
            over *= 0.6;
            if (Math.abs(over) < 0.5) over = 0;
        }
        bot.setYaw(newYaw);
        bot.setHeadYaw(newYaw);
        bot.setBodyYaw(newYaw);
        bot.setPitch(newPitch);
        return Math.abs(Aim.wrap(yaw - newYaw)) < 10F && Math.abs(pitch - newPitch) < 10F;
    }

    /** Something else set its head this tick (looking straight down to build under itself, say): idling keeps out. */
    void pinned() {
        commandedAge = body.bot.age;
    }

    // ------------------------------------------------------------------ looking about, with nothing to look at

    private int idleNext;
    private float idleYaw, idlePitch;
    private Entity idleAt;

    /**
     * Nothing has told its head where to look this tick: it looks about, lazily. At whoever is
     * near, toward the last noise, down at its hands, or off to one side; then holds a while.
     */
    void idle(Brain br) {
        DummyPlayer bot = body.bot;
        if (--idleNext <= 0) {
            idleNext = (int) ((30 + random.nextInt(90)) * (1.6 - body.hands.restless));
            idleAt = null;
            int roll = random.nextInt(100);
            PlayerEntity near = nearestPlayer(br, 24, 180F);
            Hearing.Sound heard = br.lastSound();
            if (near != null && roll < 45) {
                idleAt = near;
            } else if (heard != null && br.now() - heard.when() < 200 && roll < 65) {
                Vec3d at = heard.at().add(0, 1.2, 0);
                idleYaw = yawTo(bot.getEyePos(), at);
                idlePitch = MathHelper.clamp(pitchTo(bot.getEyePos(), at), -30F, 30F);
            } else if (roll < 75) {
                idleYaw = bot.getYaw() + (random.nextFloat() - 0.5F) * 30F;       // down at its hands
                idlePitch = 35F + random.nextFloat() * 25F;
            } else {
                idleYaw = bot.getYaw() + (random.nextFloat() - 0.5F) * 150F;
                idlePitch = -12F + random.nextFloat() * 28F;
            }
        }
        int age = commandedAge;
        if (idleAt != null && !idleAt.isRemoved() && idleAt.isAlive()) {
            Vec3d eye = bot.getEyePos();
            turnTo(yawTo(eye, idleAt.getEyePos()), pitchTo(eye, idleAt.getEyePos()), 0, 0.4, false);
        } else {
            turnTo(idleYaw, idlePitch, 0, 0.35, false);
        }
        commandedAge = age;
    }

    /** The nearest player it can see within range and within so many degrees of straight ahead, or null. */
    private PlayerEntity nearestPlayer(Brain br, double range, float within) {
        DummyPlayer bot = body.bot;
        PlayerEntity best = null;
        double bestD = range * range;
        for (PlayerEntity p : br.senses.visible(PlayerEntity.class)) {
            double d = p.squaredDistanceTo(bot);
            if (d >= bestD) continue;
            if (Math.abs(Aim.wrap(yawTo(bot.getEyePos(), p.getEyePos()) - bot.getYaw())) > within) continue;
            bestD = d;
            best = p;
        }
        return best;
    }

    // ------------------------------------------------------------------ stopping to look around

    private int aroundNext, aroundAge = -10;
    private float aroundYaw, aroundPitch, aroundWay = 1F;

    /**
     * Takes in a place as a person does on stopping: a handful of glances, mostly turning one way,
     * up a little, down a little. Call every tick for as long as it should look around.
     */
    void lookAround() {
        DummyPlayer bot = body.bot;
        if (bot.age - aroundAge > 1) {
            aroundWay = random.nextBoolean() ? 1F : -1F;
            aroundNext = 0;
        }
        aroundAge = bot.age;
        if (--aroundNext <= 0) {
            aroundNext = 7 + random.nextInt(8);
            if (random.nextInt(6) == 0) aroundWay = -aroundWay;
            aroundYaw = bot.getYaw() + aroundWay * (50F + random.nextFloat() * 50F);
            aroundPitch = -22F + random.nextFloat() * 48F;
        }
        turnTo(aroundYaw, aroundPitch, 0, 0.8, false);
    }

    // ------------------------------------------------------------------ on the move

    private int glanceNext = 80, glanceLeft;
    private float glanceOff, glancePitch;
    private Entity glanceAt;

    /**
     * Walking: mostly it looks where it's going, but now and then (when the going is easy) it
     * glances at someone near or off to one side for a moment, as people do. Its legs carry on.
     */
    void walking(Vec3d ahead, boolean easy) {
        DummyPlayer bot = body.bot;
        Vec3d eye = bot.getEyePos();
        float way = yawTo(eye, ahead);
        if (!easy) {
            glanceLeft = 0;
            turnTo(way, pitchTo(eye, ahead), 0, 1.0, true);
            return;
        }
        if (glanceLeft > 0) {
            glanceLeft--;
            if (glanceAt != null && !glanceAt.isRemoved() && glanceAt.isAlive()
                    && Math.abs(Aim.wrap(yawTo(eye, glanceAt.getEyePos()) - way)) < 100F) {
                turnTo(yawTo(eye, glanceAt.getEyePos()), pitchTo(eye, glanceAt.getEyePos()), 0, 0.7, false);
            } else {
                turnTo(way + glanceOff, glancePitch, 0, 0.6, false);
            }
            return;
        }
        if (--glanceNext <= 0) {
            glanceNext = (int) ((50 + random.nextInt(130)) * (1.6 - body.hands.restless));
            glanceLeft = 8 + random.nextInt(14);
            glanceAt = random.nextInt(100) < 60 ? nearestPlayer(body.brain, 20, 100F) : null;
            glanceOff = (random.nextBoolean() ? 1F : -1F) * (25F + random.nextFloat() * 45F);
            glancePitch = -10F + random.nextFloat() * 30F;
        }
        turnTo(way, pitchTo(eye, ahead), 0, 1.0, false);
    }

    /** Does a living thing stand where its eyes point, near enough to count as "on target" at this distance? */
    boolean onTarget(LivingEntity target, double distance) {
        return offFrom(target) <= Aim.tolerance(distance);
    }
}
