package net.unbeta.hg.bot;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Fighting like a person (design doc): the same swing, reach and damage as anyone, lost for human
 * reasons. Lower spams clicks and runs straight in, missing a quarter of its swings; Middling
 * circles; Higher strafes, times its swings to full charge and jumps for crits. The skill dial
 * scales misses.
 */
final class Fight {

    private Fight() {}

    /**
     * Its chance against a foe, from what it can see: (its weapon x its health) against (their
     * weapon x their armour x their health, assumed full unless it has landed hits). Over 1: winning.
     */
    static double score(Brain br, LivingEntity foe) {
        Body b = br.body;
        float mine = 1F;
        for (int i = 0; i < 36; i++) mine = Math.max(mine, Body.weaponDamage(b.bot.getInventory().getStack(i)));
        double me = mine * b.bot.getHealth() * (1 + b.bot.getArmor() / 20.0);
        ItemStack held = foe.getMainHandStack();
        float theirs = Math.max(1F, Body.weaponDamage(held));
        if (held.getItem() instanceof BowItem) theirs = 4F;
        int hits = br.notebook.hits.getOrDefault(foe.getUuid(), 0);
        double health = Math.max(2, 20 - hits * mine * 0.8);
        if (!(foe instanceof PlayerEntity)) health = foe.getHealth();
        double them = theirs * health * (1 + foe.getArmor() / 20.0);
        // Its own opinion of itself comes into it: a Rookie fancies its chances more than it should.
        return me / Math.max(1, them) * b.hands.judgement;
    }

    static boolean winning(Brain br, LivingEntity foe) {
        return score(br, foe) >= 1.0;
    }

    /**
     * One tick of melee against a target, in its Win Method's style and as well as its Hands allow.
     * True when the target is dead or gone. A swing only lands if its eyes are really on the
     * target just then, so a bot with a wandering aim misses the way a person does.
     */
    static boolean melee(Brain br, LivingEntity target) {
        Body b = br.body;
        if (target == null || target.isRemoved() || target.isDead()) {
            b.stopMoving();
            return true;
        }
        Hands h = b.hands;
        b.holdBestWeapon();
        br.notebook.fought.put(target.getUuid(), br.now());
        double d = b.bot.distanceTo(target);
        if (d > 2.9) {
            // Closing in: a path while they're far or out of sight; the last few blocks it just runs at them.
            boolean beeline = d < 6 && Math.abs(target.getY() - b.bot.getY()) < 1.6 && b.bot.canSee(target);
            if (beeline || b.walkTo(target.getBlockPos(), 1.5) == Body.Walk.FAILED) {
                b.aimAt(target);
                b.steer(target.getPos(), true);
            } else if (d < 6) {
                b.bot.setSprinting(true);
            }
            return false;
        }
        b.stopMoving();
        b.aimAt(target);
        Traits.Win win = br.win();
        BotConfig cfg = BotConfig.get();
        double skill = Math.max(0.25, cfg.skill);
        int t = b.bot.age;
        // Footwork: each style's, changed at no set beat, and only as tidy as its Hands.
        if (t >= b.strafeUntil) {
            boolean left = br.random.nextInt(100) < (h.leftLeaning ? 65 : 35);
            b.strafeSide = left ? 1F : -1F;
            b.strafeUntil = t + switch (win) {
                case LOWER -> 20 + br.random.nextInt(40);
                case MIDDLING -> 30 + br.random.nextInt(60);
                case HIGHER -> 7 + br.random.nextInt(18);
            };
        }
        float tidy = (float) (0.4 + 0.6 * h.footwork);
        switch (win) {
            case LOWER -> {
                b.bot.forwardSpeed = 0.6F;
                b.bot.sidewaysSpeed = b.strafeSide * 0.25F * (float) h.footwork;
            }
            case MIDDLING -> {
                b.bot.sidewaysSpeed = b.strafeSide * 0.5F * tidy;
                b.bot.forwardSpeed = d > 2.4 ? 0.4F : 0F;
            }
            case HIGHER -> {
                b.bot.sidewaysSpeed = b.strafeSide * 0.7F * tidy;
                b.bot.forwardSpeed = d > 2.2 ? 0.5F : -0.2F;
            }
        }
        // Just landed a hit: the best let go of forward for a moment, so the next one knocks back harder.
        if (b.letGo > 0) {
            b.letGo--;
            b.bot.forwardSpeed = -0.3F;
            b.bot.setSprinting(false);
            if (b.letGo == 0) b.sprintHit = true;
        } else if (b.sprintHit && b.bot.getHungerManager().getFoodLevel() > 6) {
            b.bot.forwardSpeed = Math.max(b.bot.forwardSpeed, 0.5F);
            b.bot.setSprinting(true);
        }
        // Clicking
        boolean swing;
        double miss;
        switch (win) {
            case LOWER -> {
                swing = t % (2 + br.random.nextInt(2)) == 0;          // 8-10 clicks a second
                miss = cfg.lowerMiss;
            }
            case MIDDLING -> {
                swing = t % (3 + br.random.nextInt(2)) == 0;          // 5-7 a second
                miss = cfg.middlingMiss;
            }
            default -> {
                swing = b.bot.getAttackCooldownProgress(0.5F) >= 0.95F; // timed to full charge
                miss = cfg.higherMiss;
                if (swing && b.bot.isOnGround() && d < 2.6) b.bot.setJumping(true);   // for the crit, next swing
                else b.bot.setJumping(false);
                if (swing && b.bot.isOnGround()) swing = br.random.nextInt(3) == 0;  // waits for the fall sometimes
            }
        }
        if (!swing) return false;
        b.bot.swingHand(Hand.MAIN_HAND);
        if (br.random.nextDouble() < miss * h.miss / skill) return false;
        if (!b.gaze.onTarget(target, d)) return false;               // its eyes weren't on them: a whiff
        b.bot.attack(target);
        br.notebook.hits.merge(target.getUuid(), 1, Integer::sum);
        b.sprintHit = false;
        if (br.random.nextDouble() < h.sprintReset) b.letGo = 2;
        return false;
    }

    // ------------------------------------------------------------------ the bow

    static boolean hasBow(Body b) {
        return b.has(s -> s.getItem() instanceof BowItem) && b.has(s -> s.isOf(Items.ARROW) || Moves.id(s).getPath().equals("heavens_arrow"));
    }

    /** Draws, leads a moving target a little, and looses. Returns true each time an arrow flies. */
    static boolean shoot(Brain br, LivingEntity target, int[] drawn) {
        Body b = br.body;
        b.stopMoving();
        double d = b.bot.distanceTo(target);
        // Leading a moving target takes practice: the worse its Hands, the further off its lead.
        double leadShare = 1.0 + (br.random.nextDouble() - 0.5) * Math.min(1.5, b.hands.aimError / 5.0);
        Vec3d lead = target.getVelocity().multiply(d / 3.0 * leadShare);
        Vec3d aim = target.getEyePos().add(lead).add(0, d * d * 0.0025 + d * 0.02, 0);
        b.lookAt(aim);
        if (!b.bot.isUsingItem()) {
            if (!b.hold(s -> s.getItem() instanceof BowItem)) return false;
            b.useHeld();
            drawn[0] = 0;
            return false;
        }
        if (++drawn[0] < 22) return false;
        // Shaky hands: a little wobble before letting go.
        float wobble = (float) (b.hands.aimError * 0.5 / Math.max(0.25, BotConfig.get().skill));
        b.bot.setYaw(b.bot.getYaw() + (br.random.nextFloat() - 0.5F) * wobble);
        b.bot.setPitch(MathHelper.clamp(b.bot.getPitch() + (br.random.nextFloat() - 0.5F) * wobble, -90F, 90F));
        b.bot.stopUsingItem();
        drawn[0] = 0;
        return true;
    }
}
