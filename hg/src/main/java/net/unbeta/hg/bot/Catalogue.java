package net.unbeta.hg.bot;

import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;

import java.util.List;

import static net.unbeta.hg.bot.Reaction.Outcome.RUNNING;

/**
 * Every Reaction a bot can have, in the order its triggers are checked: highest tier first, and
 * within a tier, the more pressing (or the one that uses what it already has) first.
 */
final class Catalogue {

    private Catalogue() {}

    static final List<Reaction> ALL = List.of(
            // Survival
            new Survival.OnFire(), new Survival.InLava(), new Survival.Drowning(), new Survival.SporePlanted(),
            new Survival.Creeper(), new Survival.SporeLanded(), new Survival.WaterDrop(), new Survival.Zombification(),
            new Survival.Retreat(), new Survival.HitFromBehind(), new Survival.ShotAt(), new Survival.Mimic(),
            new Survival.GuardianBeam(), new Survival.Enderman(), new Survival.Respawned(), new Survival.Border(),
            new Survival.Hungry(), new Survival.Heal(), new Survival.Dark(),
            // Combat
            new Combat.Gloat(), new Combat.HitByPlayer(), new Combat.PillarUp(), new Combat.Outnumbered(),
            new Combat.Grudge(), new Combat.Pounce(), new Combat.Terminator(), new Combat.Brandish(),
            new Combat.Followed(), new Combat.Chase(), new Combat.Bow(), new Combat.Unarmed(),
            new Combat.OthersFighting(), new Combat.Pile(), new Opportunity.Orbs(Reaction.Tier.COMBAT),
            // Need: the gear ladder
            new Needs.Full(), new Keeping.Regear(), new Needs.Forage(), new Needs.Planks(), new Needs.Table(),
            new Needs.WoodTools(), new Needs.Furnace(), new Needs.IronTools(), new Needs.Armour(), new Needs.Smelt(),
            new Needs.LavaFuel(), new Needs.Torches(), new Needs.LightTorches(), new Needs.WornTool(), new Needs.Water(),
            new Needs.Bed(), new Needs.NightHide(), new Keeping.Stash(), new Keeping.Goal(), new Needs.Ore(), new Needs.Cobble(), new Needs.Chop(),
            // Opportunity
            new Opportunity.ItemsOnGround(), new Opportunity.Orbs(Reaction.Tier.OPPORTUNITY), new Opportunity.Chest(),
            new Opportunity.EndermanXp(), new Opportunity.Vandal(), new Opportunity.Clambox(), new Opportunity.Pumpkin(),
            new Opportunity.Door(), new Opportunity.Heard());

    /**
     * Middling contagion: a Reaction of the same flavour as what it saw someone doing, or null.
     * Seen running: it runs the same way. Seen gathering: it chops or mines too. Seen crafting:
     * it crafts its next tool. (Seen fighting: joins in, once Combat arrives in step 6.)
     */
    static Reaction copy(Notebook.Sighting s, Brain br) {
        Body b = br.body;
        Reaction r = null;
        switch (s.showing) {
            case FEARFUL -> r = new Stampede(s.heading);
            case GATHERING -> {
                if (b.has(Moves::pick) && br.notebook.nearest(Notebook.Kind.ORE, b.bot.getBlockPos(), 24) != null) {
                    Reaction ore = new Needs.Ore();
                    if (ore.trigger(br)) r = ore;
                }
                if (r == null && br.notebook.nearest(Notebook.Kind.LOG, b.bot.getBlockPos(), 32) != null) r = new CopyChop();
            }
            case AGGRESSIVE -> {
                if (net.unbeta.hg.Round.phase() != net.unbeta.hg.Round.Phase.GAME) return null;
                // Joins in on the same target, or turns on the nearest player.
                Combat.Unarmed join = new Combat.Unarmed();
                r = new Combat.Chase();
                if (!r.trigger(br)) r = join.trigger(br) ? join : null;
            }
            case CRAFTING -> {
                if (!b.has(Moves::pick) && Moves.wood(b) >= 5) r = new Needs.WoodTools();
                else if (b.count(Items.IRON_INGOT) >= 3) r = new Needs.IronTools();
            }
            default -> { }
        }
        if (r != null) r.forced = true;
        return r;
    }

    /** Copied fright: runs the way they ran, for 3 s. */
    static final class Stampede extends Reaction {
        private final Vec3d heading;

        Stampede(Vec3d heading) {
            super("M1", "Stampede", Tier.COMBAT, 3, true, Tag.FEARFUL);
            this.heading = heading;
        }

        public Reaction fresh() { return new Stampede(heading); }

        public boolean trigger(Brain br) { return true; }

        public Outcome tick(Brain br) {
            Vec3d h = heading.horizontalLengthSquared() < 0.001 ? br.bot().getRotationVector() : heading;
            br.body.lookAt(br.bot().getEyePos().add(new Vec3d(h.x, 0, h.z).normalize().multiply(8)));
            br.body.run();
            return RUNNING;
        }
    }

    /** Copied chopping: a log or two from the nearest tree it knows. */
    static final class CopyChop extends Reaction {
        private Notebook.Mark tree;
        private int logs;

        CopyChop() { super("M2", "Chop (copying)", Tier.NEED, 30, false, Tag.GATHERING); }

        public Reaction fresh() { return new CopyChop(); }

        public boolean trigger(Brain br) { return true; }

        public Outcome tick(Brain br) {
            if (logs >= 2) return Outcome.DONE;
            if (tree == null || !Moves.stillThere(br, tree)) {
                tree = br.notebook.nearest(Notebook.Kind.LOG, br.bot().getBlockPos(), 32);
                if (tree == null) return Outcome.DONE;
            }
            Outcome o = Needs.mine(br, tree.pos);
            if (o == RUNNING) return RUNNING;
            br.notebook.forget(tree.kind, tree.pos);
            tree = null;
            if (o == Outcome.DONE) logs++;
            return RUNNING;
        }
    }
}
