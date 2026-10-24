package net.unbeta.hg.bot;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

import static net.unbeta.hg.bot.Reaction.Outcome.DONE;
import static net.unbeta.hg.bot.Reaction.Outcome.FAILED;
import static net.unbeta.hg.bot.Reaction.Outcome.RUNNING;
import static net.unbeta.hg.bot.Reaction.Tag.AGGRESSIVE;
import static net.unbeta.hg.bot.Reaction.Tag.FEARFUL;
import static net.unbeta.hg.bot.Reaction.Tag.HIDING;
import static net.unbeta.hg.bot.Reaction.Tag.LOOTING;
import static net.unbeta.hg.bot.Reaction.Tag.SOCIAL;
import static net.unbeta.hg.bot.Reaction.Tier.COMBAT;

/** The Combat table (design doc): a fight starting, happening or worth starting. Only in the game proper. */
final class Combat {

    private Combat() {}

    /** Foe players it can see now, nearest first. */
    static List<PlayerEntity> foes(Brain br, double range) {
        List<PlayerEntity> out = new ArrayList<>();
        for (PlayerEntity p : br.senses.visible(PlayerEntity.class)) {
            if (br.isFoe(p) && p.squaredDistanceTo(br.bot()) <= range * range) out.add(p);
        }
        out.sort((a, b) -> Double.compare(a.squaredDistanceTo(br.bot()), b.squaredDistanceTo(br.bot())));
        return out;
    }

    static boolean armed(Brain br, PlayerEntity p) {
        Notebook.Sighting s = br.notebook.sightings.get(p.getUuid());
        return s != null && s.armed;
    }

    static boolean newlyHitBy(Brain br, LivingEntity[] who) {
        LivingEntity a = br.bot().getAttacker();
        if (a == null || br.bot().hurtTime < 5) return false;
        who[0] = a;
        return true;
    }

    // ------------------------------------------------------------------ C1 an unarmed player

    static final class Unarmed extends Reaction {
        Unarmed() { super("C1", "Unarmed player", COMBAT, 8, true, AGGRESSIVE); }
        public Reaction fresh() { return new Unarmed(); }

        private PlayerEntity target;
        private boolean watchOnly;

        public boolean trigger(Brain br) {
            for (PlayerEntity p : foes(br, 20)) {
                if (armed(br, p)) continue;
                Traits.Play play = br.traits().play();
                if (play == Traits.Play.HOG && (br.hogMark == null || p.getBlockPos().getSquaredDistance(br.hogMark) > 100)) continue;
                if (play == Traits.Play.TORTOISE && !br.terminator()) continue;
                switch (br.win()) {
                    case HIGHER -> {
                        if (!br.body.has(Moves::weapon) || !Fight.winning(br, p)) continue;
                    }
                    case MIDDLING -> {
                        boolean others = false;
                        for (Notebook.Sighting s : br.notebook.recent(br.now())) {
                            if (s.showing == AGGRESSIVE && !s.id.equals(p.getUuid()) && s.pos.distanceTo(p.getPos()) < 8) others = true;
                        }
                        watchOnly = !others;
                    }
                    default -> { }
                }
                target = p;
                because = p.getName().getString() + " has no weapon";
                return true;
            }
            return false;
        }

        public Outcome tick(Brain br) {
            if (watchOnly) {
                if (br.bot().distanceTo(target) > 8) br.body.walkTo(target.getBlockPos(), 7);
                else br.body.stopMoving();
                br.body.lookAt(target.getEyePos());
                return RUNNING;
            }
            return Fight.melee(br, target) ? DONE : RUNNING;
        }
    }

    // ------------------------------------------------------------------ C2 hit by a player

    static final class HitByPlayer extends Reaction {
        HitByPlayer() { super("C2", "Hit by a player", COMBAT, 6, true, AGGRESSIVE, FEARFUL); }
        public Reaction fresh() { return new HitByPlayer(); }

        private PlayerEntity foe;
        private boolean fight;

        public boolean trigger(Brain br) {
            LivingEntity[] who = new LivingEntity[1];
            if (!newlyHitBy(br, who) || !(who[0] instanceof PlayerEntity p) || !br.isFoe(p)) return false;
            if (br.handledC2 == br.bot().getLastAttackedTime()) return false;
            br.handledC2 = br.bot().getLastAttackedTime();
            foe = p;
            fight = Fight.winning(br, p) || br.traits().keep() == Traits.Keep.BULWARK;
            because = p.getName().getString() + " hit me; " + (fight ? "I can take them" : "I can't win this");
            return true;
        }

        public Outcome tick(Brain br) {
            if (!fight) {
                br.body.flee(foe.getPos());
                return RUNNING;
            }
            return Fight.melee(br, foe) ? DONE : RUNNING;
        }
    }

    // ------------------------------------------------------------------ C3 chase a runner

    static final class Chase extends Reaction {
        Chase() { super("C3", "Chase", COMBAT, 8, true, AGGRESSIVE); }
        public Reaction fresh() { return new Chase(); }

        private PlayerEntity foe;

        public boolean trigger(Brain br) {
            for (PlayerEntity p : foes(br, 24)) {
                if (br.notebook.hits.getOrDefault(p.getUuid(), 0) < 4) continue;
                Notebook.Sighting s = br.notebook.sightings.get(p.getUuid());
                if (s == null || s.heading.dotProduct(p.getPos().subtract(br.bot().getPos())) <= 0) continue;
                foe = p;
                because = "I've hit " + p.getName().getString() + " 4 times and they're running";
                return true;
            }
            return false;
        }

        public Outcome tick(Brain br) {
            if (!br.senses.sees(foe) && br.win() != Traits.Win.LOWER) return DONE;
            return Fight.melee(br, foe) ? DONE : RUNNING;
        }
    }

    // ------------------------------------------------------------------ C4 outnumbered

    static final class Outnumbered extends Reaction {
        Outnumbered() { super("C4", "Outnumbered", COMBAT, 6, true, FEARFUL); }
        public Reaction fresh() { return new Outnumbered(); }

        private Vec3d from;
        private int t;

        public boolean trigger(Brain br) {
            List<PlayerEntity> armed = new ArrayList<>();
            for (PlayerEntity p : foes(br, 10)) {
                Notebook.Sighting s = br.notebook.sightings.get(p.getUuid());
                if (armed(br, p) && (s == null || s.showing != AGGRESSIVE)) armed.add(p);
            }
            if (armed.size() < 2) return false;
            Vec3d c = Vec3d.ZERO;
            for (PlayerEntity p : armed) c = c.add(p.getPos());
            from = c.multiply(1.0 / armed.size());
            because = armed.size() + " armed players near me";
            return true;
        }

        public Outcome tick(Brain br) {
            if (t++ < 20) {
                br.body.lookAt(from.add(0, 1.6, 0));
                br.bot().forwardSpeed = -0.6F;
                return RUNNING;
            }
            br.body.flee(from);
            return RUNNING;
        }
    }

    // ------------------------------------------------------------------ C5 two others fighting

    static final class OthersFighting extends Reaction {
        OthersFighting() { super("C5", "Others fighting", COMBAT, 15, true, AGGRESSIVE); }
        public Reaction fresh() { return new OthersFighting(); }

        private PlayerEntity a, b;
        private PlayerEntity winner;

        public boolean trigger(Brain br) {
            if (br.win() == Traits.Win.MIDDLING && br.traits().play() != Traits.Play.HARE) return false;   // contagion's job
            List<PlayerEntity> near = foes(br, 24);
            for (int i = 0; i < near.size(); i++) for (int j = i + 1; j < near.size(); j++) {
                PlayerEntity x = near.get(i), y = near.get(j);
                if (x.squaredDistanceTo(y) > 25) continue;
                if (x.hurtTime == 0 && y.hurtTime == 0 && !x.handSwinging && !y.handSwinging) continue;
                a = x;
                b = y;
                because = x.getName().getString() + " and " + y.getName().getString() + " are fighting";
                return true;
            }
            return false;
        }

        public Outcome tick(Brain br) {
            Body body = br.body;
            boolean hare = br.traits().play() == Traits.Play.HARE;
            if (winner != null) return Fight.melee(br, winner) ? DONE : RUNNING;
            if (a.isDead() || b.isDead()) {
                winner = a.isDead() ? b : a;
                if (winner.isDead() || !hare) return DONE;
                return RUNNING;
            }
            if (br.win() == Traits.Win.LOWER && !hare) {
                // Joins in against whoever's getting hurt.
                PlayerEntity loser = a.hurtTime >= b.hurtTime ? a : b;
                return Fight.melee(br, loser) ? DONE : RUNNING;
            }
            Vec3d mid = a.getPos().add(b.getPos()).multiply(0.5);
            if (!hare && br.bot().getHealth() < 14) {
                body.flee(mid);
                return RUNNING;
            }
            double d = br.bot().getPos().distanceTo(mid);
            if (d < 14) body.flee(mid);
            else if (d > 18) body.walkTo(BlockPos.ofFloored(mid), 16);
            else body.stopMoving();
            body.lookAt(mid.add(0, 1.5, 0));
            return RUNNING;
        }
    }

    // ------------------------------------------------------------------ C6 the one who killed it

    static final class Grudge extends Reaction {
        Grudge() { super("C6", "Grudge", COMBAT, 20, true, AGGRESSIVE); }
        public Reaction fresh() { return new Grudge(); }

        private PlayerEntity foe;
        private boolean avoid;

        public boolean trigger(Brain br) {
            if (br.notebook.grudge == null) return false;
            for (PlayerEntity p : foes(br, 32)) {
                if (!p.getUuid().equals(br.notebook.grudge)) continue;
                switch (br.win()) {
                    case MIDDLING -> avoid = true;
                    case HIGHER -> {
                        if (!br.body.has(Moves::weapon) || br.bot().getArmor() == 0 || !Fight.winning(br, p)) return false;
                    }
                    default -> { }
                }
                foe = p;
                because = p.getName().getString() + " killed me";
                return true;
            }
            return false;
        }

        public Outcome tick(Brain br) {
            if (avoid) {
                br.body.flee(foe.getPos());
                return br.bot().distanceTo(foe) > 40 ? DONE : RUNNING;
            }
            return Fight.melee(br, foe) ? DONE : RUNNING;
        }
    }

    // ------------------------------------------------------------------ C7 bow

    static final class Bow extends Reaction {
        Bow() { super("C7", "Shoot", COMBAT, 4, true, AGGRESSIVE); }
        public Reaction fresh() { return new Bow(); }

        private PlayerEntity foe;
        private final int[] drawn = {0};

        public boolean trigger(Brain br) {
            if (!Fight.hasBow(br.body)) return false;
            Traits.Keep k = br.traits().keep();
            double min = (k == Traits.Keep.DUELIST || k == Traits.Keep.FLETCHER || br.traits().play() == Traits.Play.TORTOISE) ? 5 : 8;
            for (PlayerEntity p : foes(br, 25)) {
                double d = p.distanceTo(br.bot());
                if (d < min) continue;
                if (br.traits().play() == Traits.Play.TORTOISE && !br.terminator() && !p.getUuid().equals(br.notebook.grudge)) continue;
                foe = p;
                because = p.getName().getString() + " in bow range";
                return true;
            }
            return false;
        }

        public Outcome tick(Brain br) {
            if (foe.isDead() || !br.senses.sees(foe)) return DONE;
            Fight.shoot(br, foe, drawn);
            return RUNNING;
        }

        public void stopped(Brain br) {
            br.bot().clearActiveItem();
        }
    }

    // ------------------------------------------------------------------ C8 the pile

    static final class Pile extends Reaction {
        Pile() { super("C8", "Loot the pile", COMBAT, 20, false, LOOTING); }
        public Reaction fresh() { return new Pile(); }

        private BlockPos pile;
        private boolean guard;
        private long until;

        public boolean trigger(Brain br) {
            BlockPos at = br.bot().getBlockPos();
            for (var e : br.notebook.piles.entrySet()) {
                if (br.now() - e.getValue() > 6000 || e.getKey().getSquaredDistance(at) > 32 * 32) continue;
                pile = e.getKey();
                guard = br.traits().play() == Traits.Play.HOG;
                because = "someone I fought died at " + pile.toShortString();
                return true;
            }
            return false;
        }

        public Outcome tick(Brain br) {
            if (guard) {
                if (until == 0) until = br.now() + BotConfig.get().hogGuardTicks;
                if (br.now() > until) {
                    br.notebook.piles.remove(pile);
                    return DONE;
                }
                if (br.bot().getBlockPos().getSquaredDistance(pile) > 4) br.body.walkTo(pile, 1.0);
                else Errands.fidget(br);
                return RUNNING;
            }
            ItemEntity item = null;
            for (ItemEntity it : br.senses.visible(ItemEntity.class)) {
                if (it.getBlockPos().getSquaredDistance(pile) < 36 && !Moves.boomSpore(it.getStack())) item = it;
            }
            if (item == null) {
                if (br.bot().getBlockPos().getSquaredDistance(pile) > 9) {
                    return br.body.walkTo(pile, 2.0) == Body.Walk.FAILED ? FAILED : RUNNING;
                }
                br.notebook.piles.remove(pile);
                return DONE;
            }
            br.body.walkTo(item.getBlockPos(), 0.4);
            return RUNNING;
        }
    }

    // ------------------------------------------------------------------ C9 followed

    static final class Followed extends Reaction {
        Followed() { super("C9", "Followed", COMBAT, 8, true, AGGRESSIVE); }
        public Reaction fresh() { return new Followed(); }

        private PlayerEntity foe;
        private boolean run;
        private boolean poured;

        public boolean trigger(Brain br) {
            for (PlayerEntity p : foes(br, 24)) {
                Notebook.Sighting s = br.notebook.sightings.get(p.getUuid());
                if (s == null || s.closeSince < 0 || br.now() - s.closeSince < 600) continue;
                foe = p;
                run = br.win() == Traits.Win.MIDDLING || (br.win() == Traits.Win.HIGHER && !Fight.winning(br, p));
                s.closeSince = br.now();   // noticed: start counting again
                because = p.getName().getString() + " has been following me";
                return true;
            }
            return false;
        }

        public Outcome tick(Brain br) {
            Body b = br.body;
            if (!poured && br.traits().keep() == Traits.Keep.COPPERHEAD && b.has(Moves::lavaBucket)) {
                // The Copperhead's trick: lava on the ground between them.
                b.hold(Moves::lavaBucket);
                Vec3d between = b.bot.getPos().add(foe.getPos().subtract(b.bot.getPos()).normalize().multiply(2));
                if (b.lookAt(between)) {
                    b.useHeld();
                    poured = true;
                }
                return RUNNING;
            }
            if (run) {
                Notebook.Sighting other = null;
                for (Notebook.Sighting s : br.notebook.recent(br.now())) {
                    if (!s.id.equals(foe.getUuid()) && (other == null || s.pos.distanceTo(b.bot.getPos()) < other.pos.distanceTo(b.bot.getPos()))) other = s;
                }
                if (other != null) b.walkTo(BlockPos.ofFloored(other.pos), 4);
                else b.flee(foe.getPos());
                return RUNNING;
            }
            return Fight.melee(br, foe) ? DONE : RUNNING;
        }
    }

    // ------------------------------------------------------------------ C10 pillar up

    static final class PillarUp extends Reaction {
        PillarUp() { super("C10", "Pillar up", COMBAT, 8, true, HIDING); }
        public Reaction fresh() { return new PillarUp(); }

        private LivingEntity foe;
        private final Moves.Pillar pillar = new Moves.Pillar(3);

        public boolean trigger(Brain br) {
            if (br.body.count(Moves::buildingBlock) < 3) return false;
            LivingEntity a = br.bot().getAttacker();
            if (!(a instanceof PlayerEntity) || br.bot().age - br.bot().getLastAttackedTime() > 40 || a.distanceTo(br.bot()) > 5) return false;
            if (br.traits().keep() != Traits.Keep.BUILDER && Fight.winning(br, a)) return false;
            foe = a;
            because = "losing to " + a.getName().getString() + ": up a pillar";
            return true;
        }

        public Outcome tick(Brain br) {
            if (!pillar.done()) {
                if (pillar.tick(br) == FAILED) return FAILED;
                return RUNNING;
            }
            br.body.stopMoving();
            br.body.lookAt(foe.getEyePos());
            if (br.bot().distanceTo(foe) < 3.5 && br.bot().getAttackCooldownProgress(0.5F) > 0.9F) {
                br.body.holdBestWeapon();
                br.bot().attack(foe);
                br.bot().swingHand(net.minecraft.util.Hand.MAIN_HAND);
            }
            return RUNNING;
        }
    }

    // ------------------------------------------------------------------ C11 Hog: brandish

    static final class Brandish extends Reaction {
        Brandish() { super("C11", "Brandish", COMBAT, 5, true, AGGRESSIVE); }
        public Reaction fresh() { return new Brandish(); }

        private PlayerEntity foe;
        private Vec3d stepTo;
        private boolean fighting;

        public boolean trigger(Brain br) {
            if (br.traits().play() != Traits.Play.HOG || br.hogMark == null) return false;
            if (br.bot().getBlockPos().getSquaredDistance(br.hogMark) > 64) return false;
            for (PlayerEntity p : foes(br, 16)) {
                if (p.getBlockPos().getSquaredDistance(br.hogMark) > 100) continue;
                foe = p;
                because = p.getName().getString() + " came near my spot";
                return true;
            }
            return false;
        }

        public Outcome tick(Brain br) {
            Body b = br.body;
            if (fighting) return Fight.melee(br, foe) ? DONE : RUNNING;
            b.holdBestWeapon();
            if (stepTo == null) stepTo = b.bot.getPos().add(foe.getPos().subtract(b.bot.getPos()).normalize().multiply(2));
            if (b.bot.getPos().squaredDistanceTo(stepTo) > 0.5) {
                b.lookAt(stepTo.add(0, 1.6, 0));
                b.bot.forwardSpeed = 0.5F;
            } else {
                b.stopMoving();
                b.lookAt(foe.getEyePos());
            }
            if (foe.distanceTo(b.bot) < 6 || b.bot.getAttacker() == foe && b.bot.hurtTime > 0) {
                fighting = true;
                endsAt = Math.max(endsAt, br.now() + 200);
            }
            if (foe.getBlockPos().getSquaredDistance(br.hogMark) > 144) return DONE;   // backed off: let them go
            return RUNNING;
        }
    }

    // ------------------------------------------------------------------ C12 Hare: pounce

    static final class Pounce extends Reaction {
        Pounce() { super("C12", "Pounce", COMBAT, 10, true, AGGRESSIVE); }
        public Reaction fresh() { return new Pounce(); }

        private PlayerEntity quarry;
        private int hitsAtStart;

        public boolean trigger(Brain br) {
            if (br.traits().play() != Traits.Play.HARE || br.quarry == null) return false;
            for (PlayerEntity p : foes(br, 32)) {
                if (!p.getUuid().equals(br.quarry)) continue;
                Notebook.Sighting s = br.notebook.sightings.get(p.getUuid());
                boolean busy = s != null && (s.showing == Tag.GATHERING || s.showing == Tag.CRAFTING);
                boolean backTurned = p.getRotationVector().dotProduct(br.bot().getPos().subtract(p.getPos()).normalize()) < 0;
                boolean hurt = p.hurtTime > 0;
                if (!((busy && backTurned) || hurt)) continue;
                quarry = p;
                hitsAtStart = br.notebook.hits.getOrDefault(p.getUuid(), 0);
                because = p.getName().getString() + (hurt ? " just got hurt" : " is busy with their back to me");
                return true;
            }
            return false;
        }

        public Outcome tick(Brain br) {
            // A Hare backs off after 3 hits to see if you run.
            if (br.notebook.hits.getOrDefault(quarry.getUuid(), 0) - hitsAtStart >= 3) return DONE;
            br.bot().setSprinting(true);
            return Fight.melee(br, quarry) ? DONE : RUNNING;
        }
    }

    // ------------------------------------------------------------------ Tortoise: Terminator

    static final class Terminator extends Reaction {
        Terminator() { super("T1", "Terminate", COMBAT, 20, true, AGGRESSIVE); }
        public Reaction fresh() { return new Terminator(); }

        private PlayerEntity foe;

        public boolean trigger(Brain br) {
            if (br.traits().play() != Traits.Play.TORTOISE || !br.terminator()) return false;
            for (PlayerEntity p : foes(br, 32)) {
                if (!Errands.isolated(br, p.getUuid())) continue;
                foe = p;
                because = p.getName().getString() + " is alone";
                return true;
            }
            return false;
        }

        public Outcome tick(Brain br) {
            return Fight.melee(br, foe) ? DONE : RUNNING;
        }
    }

    // ------------------------------------------------------------------ F3 Lower: after a kill

    static final class Gloat extends Reaction {
        Gloat() { super("F3", "Gloat", COMBAT, 2, false, SOCIAL); }
        public Reaction fresh() { return new Gloat(); }

        public boolean trigger(Brain br) {
            if (br.win() != Traits.Win.LOWER || br.now() - br.lastKillAt > 10) return false;
            br.lastKillAt = -1000;
            because = "got a kill";
            return true;
        }

        public Outcome tick(Brain br) {
            br.body.stopMoving();
            br.bot().setSneaking((br.bot().age / 3) % 2 == 0);
            return RUNNING;
        }

        public void stopped(Brain br) {
            br.bot().setSneaking(false);
        }
    }

    /** Does anyone (else) stand near this entity? */
    static boolean alone(Brain br, Entity e) {
        for (PlayerEntity p : br.senses.visible(PlayerEntity.class)) if (p != e && p.distanceTo(e) < 24) return false;
        return true;
    }
}
