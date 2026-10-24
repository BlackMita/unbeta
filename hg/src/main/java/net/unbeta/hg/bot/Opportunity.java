package net.unbeta.hg.bot;

import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import static net.unbeta.hg.bot.Reaction.Outcome.DONE;
import static net.unbeta.hg.bot.Reaction.Outcome.FAILED;
import static net.unbeta.hg.bot.Reaction.Outcome.RUNNING;
import static net.unbeta.hg.bot.Reaction.Tag.AGGRESSIVE;
import static net.unbeta.hg.bot.Reaction.Tag.CRAFTING;
import static net.unbeta.hg.bot.Reaction.Tag.LOOTING;
import static net.unbeta.hg.bot.Reaction.Tier.COMBAT;
import static net.unbeta.hg.bot.Reaction.Tier.OPPORTUNITY;

/** The Opportunity table (design doc): something free or tempting nearby. O5 is folded into O3. */
final class Opportunity {

    private Opportunity() {}

    // ------------------------------------------------------------------ O1 items on the ground

    static final class ItemsOnGround extends Reaction {
        ItemsOnGround() { super("O1", "Pick up items", OPPORTUNITY, 20, false, LOOTING); }
        public Reaction fresh() { return new ItemsOnGround(); }

        private ItemEntity item;
        private boolean guard;
        private long until;

        public boolean trigger(Brain br) {
            if (!Moves.emptySlot(br.body)) return false;
            boolean packRat = br.traits().keep() == Traits.Keep.PACK_RAT;
            ItemEntity best = null;
            for (ItemEntity it : br.senses.visible(ItemEntity.class)) {
                ItemStack s = it.getStack();
                if (Moves.boomSpore(s) || it.squaredDistanceTo(br.bot()) > 256) continue;
                if (!packRat && Keeping.score(br, s) < 1) continue;
                if (best == null || it.squaredDistanceTo(br.bot()) < best.squaredDistanceTo(br.bot())) best = it;
            }
            if (best == null) return false;
            item = best;
            guard = br.traits().play() == Traits.Play.HOG && br.random.nextBoolean();
            because = best.getStack().getName().getString() + " lying there" + (guard ? " (guarding it)" : "");
            return true;
        }

        public Outcome tick(Brain br) {
            if (guard) {
                // Hogs guard piles rather than grab them.
                if (until == 0) until = br.now() + BotConfig.get().hogGuardTicks;
                if (item.isRemoved() || br.now() > until) return DONE;
                if (br.bot().squaredDistanceTo(item) > 4) br.body.walkTo(item.getBlockPos(), 1.0);
                else Errands.fidget(br);
                return RUNNING;
            }
            if (item.isRemoved()) return DONE;
            return br.body.walkTo(item.getBlockPos(), 0.4) == Body.Walk.FAILED ? FAILED : RUNNING;
        }
    }

    // ------------------------------------------------------------------ O2 experience orbs

    static final class Orbs extends Reaction {
        Orbs(Tier tier) { super(tier == COMBAT ? "O2h" : "O2", "Collect experience", tier, 15, false, LOOTING); }
        public Reaction fresh() { return new Orbs(tier); }

        private ExperienceOrbEntity orb;

        public boolean trigger(Brain br) {
            boolean hare = br.traits().play() == Traits.Play.HARE;
            if ((tier == COMBAT) != hare) return false;   // Hares chase experience harder
            for (ExperienceOrbEntity o : br.senses.visible(ExperienceOrbEntity.class)) {
                if (o.squaredDistanceTo(br.bot()) > 256) continue;
                orb = o;
                because = "experience orbs";
                return true;
            }
            return false;
        }

        public Outcome tick(Brain br) {
            if (orb.isRemoved()) {
                for (ExperienceOrbEntity o : br.senses.visible(ExperienceOrbEntity.class)) {
                    if (o.squaredDistanceTo(br.bot()) < 256) {
                        orb = o;
                        return RUNNING;
                    }
                }
                return DONE;
            }
            return br.body.walkTo(orb.getBlockPos(), 0.4) == Body.Walk.FAILED ? FAILED : RUNNING;
        }
    }

    // ------------------------------------------------------------------ O3 a chest

    static final class Chest extends Reaction {
        Chest() { super("O3", "Loot a chest", OPPORTUNITY, 15, false, LOOTING); }
        public Reaction fresh() { return new Chest(); }

        private Notebook.Mark chest;
        private int t;

        public boolean trigger(Brain br) {
            if (br.traits().play() == Traits.Play.HOG || !Moves.emptySlot(br.body)) return false;
            BlockPos at = br.bot().getBlockPos();
            chest = br.notebook.nearest(Notebook.Kind.CHEST, at, 32, m -> !br.notebook.opened.contains(m.pos)
                    && !m.what.startsWith("my") && br.notebook.nearest(Notebook.Kind.DANGER, m.pos, 3) == null);
            if (chest == null) return false;
            because = "a chest at " + chest.pos.toShortString();
            return true;
        }

        public Outcome tick(Brain br) {
            Body b = br.body;
            if (!Moves.stillThere(br, chest)) return FAILED;
            if (!b.inReach(chest.pos)) return b.walkTo(chest.pos, 2.5) == Body.Walk.FAILED ? FAILED : RUNNING;
            b.stopMoving();
            b.lookAt(Vec3d.ofCenter(chest.pos));
            if (t++ == 0) {
                br.notebook.opened.add(chest.pos);
                b.useOn(chest.pos);
                return RUNNING;
            }
            if (t < 20 + br.random.nextInt(20)) return RUNNING;     // looks through it for 1 to 2 s
            if (br.world().getBlockEntity(chest.pos) instanceof Inventory inv) Keeping.takeBest(br, inv);
            if (b.bot.currentScreenHandler != b.bot.playerScreenHandler) b.bot.closeHandledScreen();
            return DONE;
        }

        public void stopped(Brain br) {
            if (br.bot().currentScreenHandler != br.bot().playerScreenHandler) br.bot().closeHandledScreen();
        }
    }

    // ------------------------------------------------------------------ O4 Enderman held back by light

    static final class EndermanXp extends Reaction {
        EndermanXp() { super("O4", "Strike an Enderman", OPPORTUNITY, 6, true, AGGRESSIVE); }
        public Reaction fresh() { return new EndermanXp(); }

        private EndermanEntity man;

        public boolean trigger(Brain br) {
            if (br.bot().getHealth() < br.bot().getMaxHealth()) return false;
            if (br.world().getLightLevel(br.bot().getBlockPos()) < 10) return false;
            if (br.win() == Traits.Win.HIGHER && !br.senses.visible(PlayerEntity.class).isEmpty()) return false;
            for (EndermanEntity e : br.senses.visible(EndermanEntity.class)) {
                if (e.squaredDistanceTo(br.bot()) < 25) {
                    man = e;
                    because = "an Enderman held back by my light";
                    return true;
                }
            }
            return false;
        }

        public Outcome tick(Brain br) {
            if (man.isRemoved()) return DONE;
            br.body.lookAt(man.getEyePos());
            if (br.bot().distanceTo(man) < 3 && br.bot().getAttackCooldownProgress(0.5F) > 0.9F) {
                br.bot().attack(man);
                br.bot().swingHand(net.minecraft.util.Hand.MAIN_HAND);
            }
            return RUNNING;
        }
    }

    // ------------------------------------------------------------------ O6 Hare: vandal

    static final class Vandal extends Reaction {
        Vandal() { super("O6", "Vandalise", OPPORTUNITY, 10, true, AGGRESSIVE); }
        public Reaction fresh() { return new Vandal(); }

        private Notebook.Mark target;

        public boolean trigger(Brain br) {
            if (br.traits().play() != Traits.Play.HARE) return false;
            if (br.quarry != null) {
                for (PlayerEntity p : br.senses.visible(PlayerEntity.class)) if (p.getUuid().equals(br.quarry)) return false;
            }
            BlockPos at = br.bot().getBlockPos();
            for (Notebook.Kind k : new Notebook.Kind[]{Notebook.Kind.PLACED, Notebook.Kind.TABLE, Notebook.Kind.FURNACE}) {
                Notebook.Mark m = br.notebook.nearest(k, at, 16, x -> !x.what.startsWith("my"));
                if (m != null && br.body.canHarvest(br.world().getBlockState(m.pos))) {
                    target = m;
                    because = "someone's " + m.what + " - break it";
                    return true;
                }
            }
            return false;
        }

        public Outcome tick(Brain br) {
            Reaction.Outcome o = Needs.mine(br, target.pos);
            if (o != RUNNING) br.notebook.forget(target.kind, target.pos);
            return o;
        }
    }

    // ------------------------------------------------------------------ O7 Clambox

    static final class Clambox extends Reaction {
        Clambox() { super("O7", "Crack a clambox", OPPORTUNITY, 40, false, LOOTING); }
        public Reaction fresh() { return new Clambox(); }

        private Notebook.Mark box;

        public boolean trigger(Brain br) {
            double range = br.traits().keep() == Traits.Keep.PEARL_DIVER ? 64 : 24;
            box = br.notebook.nearest(Notebook.Kind.CLAMBOX, br.bot().getBlockPos(), range);
            if (box == null) return false;
            because = "a clambox at " + box.pos.toShortString();
            return true;
        }

        public Outcome tick(Brain br) {
            Reaction.Outcome o = Needs.mine(br, box.pos);
            if (o != RUNNING) br.notebook.forget(box.kind, box.pos);
            return o;
        }
    }

    // ------------------------------------------------------------------ O8 Lightener: pumpkins

    static final class Pumpkin extends Reaction {
        Pumpkin() { super("O8", "Shear a pumpkin", OPPORTUNITY, 15, false, CRAFTING); }
        public Reaction fresh() { return new Pumpkin(); }

        private Notebook.Mark pumpkin;
        private int t;

        public boolean trigger(Brain br) {
            if (br.traits().keep() != Traits.Keep.LIGHTENER || !br.body.has(s -> s.isOf(Items.SHEARS))) return false;
            pumpkin = br.notebook.nearest(Notebook.Kind.PUMPKIN, br.bot().getBlockPos(), 16);
            if (pumpkin == null) return false;
            because = "a pumpkin to make a Jack o'Lantern of";
            return true;
        }

        public Outcome tick(Brain br) {
            Body b = br.body;
            if (!Moves.stillThere(br, pumpkin)) return DONE;
            if (!b.inReach(pumpkin.pos)) return b.walkTo(pumpkin.pos, 2.5) == Body.Walk.FAILED ? FAILED : RUNNING;
            b.stopMoving();
            b.hold(s -> s.isOf(Items.SHEARS));
            b.lookAt(Vec3d.ofCenter(pumpkin.pos));
            if (t++ % 10 == 5) b.useOn(pumpkin.pos);
            return t > 40 ? FAILED : RUNNING;
        }
    }

    // ------------------------------------------------------------------ O9 an Obsidian Door

    static final class Door extends Reaction {
        Door() { super("O9", "Through an Obsidian Door", OPPORTUNITY, 30, true, LOOTING); }
        public Reaction fresh() { return new Door(); }

        private Notebook.Mark door;
        private boolean opened;
        private BlockPos beyond;

        public boolean trigger(Brain br) {
            if (br.traits().keep() != Traits.Keep.BANKER && br.win() != Traits.Win.HIGHER) return false;
            if (br.bot().experienceLevel < 6) return false;   // 3 levels to spare
            door = br.notebook.nearest(Notebook.Kind.DOOR, br.bot().getBlockPos(), 32, m -> !br.notebook.opened.contains(m.pos));
            if (door == null) return false;
            because = "an Obsidian Door, and levels to spare";
            return true;
        }

        public Outcome tick(Brain br) {
            Body b = br.body;
            if (beyond != null) {
                Body.Walk w = b.walkTo(beyond, 1.5);
                return w == Body.Walk.WALKING ? RUNNING : DONE;
            }
            if (!b.inReach(door.pos)) return b.walkTo(door.pos, 2.0) == Body.Walk.FAILED ? FAILED : RUNNING;
            b.stopMoving();
            b.lookAt(Vec3d.ofCenter(door.pos));
            if (!opened) {
                b.useOn(door.pos);
                opened = true;
                br.notebook.opened.add(door.pos);
                Vec3d through = Vec3d.ofCenter(door.pos).subtract(b.bot.getPos()).multiply(1, 0, 1).normalize().multiply(6);
                beyond = BlockPos.ofFloored(Vec3d.ofCenter(door.pos).add(through));
            }
            return RUNNING;
        }
    }

    // ------------------------------------------------------------------ H1 heard something

    static final class Heard extends Reaction {
        Heard() { super("H1", "Heard something", OPPORTUNITY, 2, true); }
        public Reaction fresh() { return new Heard(); }

        private Hearing.Sound sound;

        public boolean trigger(Brain br) {
            Hearing.Sound s = br.lastSound();
            if (s == null || br.now() - s.when() > 20 || s.at().distanceTo(br.bot().getPos()) < 3) return false;
            Vec3d to = s.at().subtract(br.bot().getPos()).normalize();
            if (to.dotProduct(br.bot().getRotationVector()) > 0.5) return false;   // already looking that way
            sound = s;
            br.forgetSound();
            because = "heard " + s.what();
            return true;
        }

        public Outcome tick(Brain br) {
            boolean go = sound.what().equals("a fight")
                    && (br.win() == Traits.Win.LOWER || br.traits().play() == Traits.Play.HARE);
            if (go) br.body.walkTo(BlockPos.ofFloored(sound.at()), 6);
            else br.body.stopMoving();
            br.body.lookAt(sound.at().add(0, 1.5, 0));
            return RUNNING;
        }
    }
}
