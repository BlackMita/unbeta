package net.unbeta.hg.bot;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.BedItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.SwordItem;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.unbeta.hg.DummyPlayer;

import java.util.List;

import static net.unbeta.hg.bot.Reaction.Outcome.DONE;
import static net.unbeta.hg.bot.Reaction.Outcome.FAILED;
import static net.unbeta.hg.bot.Reaction.Outcome.RUNNING;
import static net.unbeta.hg.bot.Reaction.Tag.CRAFTING;
import static net.unbeta.hg.bot.Reaction.Tag.GATHERING;
import static net.unbeta.hg.bot.Reaction.Tier.NEED;

/**
 * The Need table (design doc): the gear ladder, as Unbeta has it. Wood first (a table, a wood
 * pick and sword); cobblestone comes from ore (a mined ore leaves it behind) and caves, since a
 * wood pick can't break stone; then a furnace, which has to be lit; then iron. Torches come unlit
 * and are lit at a flame. N16 (hide at night) is S12's job; N19 arrives with Keep Methods.
 */
final class Needs {

    private Needs() {}

    /** Runs an Orders.Craft inside a Reaction. */
    static Reaction.Outcome craft(Brain br, Orders.Craft c) {
        if (!c.tick(br.body)) return RUNNING;
        return c.succeeded ? DONE : FAILED;
    }

    /** Walks into reach of a block and breaks it. DONE once it's gone. */
    static Reaction.Outcome mine(Brain br, BlockPos pos) {
        Body b = br.body;
        if (br.world().getBlockState(pos).isAir()) return DONE;
        if (!b.inReach(pos)) {
            if (b.walkTo(pos, 2.5) == Body.Walk.FAILED) return FAILED;
            if (!b.inReach(pos)) return RUNNING;
        }
        return switch (b.dig(pos)) {
            case DONE -> DONE;
            case FAILED -> FAILED;
            default -> RUNNING;
        };
    }

    // ------------------------------------------------------------------ N1 chop

    static final class Chop extends Reaction {
        Chop() { super("N1", "Chop a tree", NEED, 60, false, GATHERING); }
        public Reaction fresh() { return new Chop(); }

        private Notebook.Mark tree;
        private BlockPos log;

        /** Wood it wants in hand, in planks (a log is 4). */
        static int wanted(Brain br) {
            boolean table = Moves.tableAt(br, 16);
            boolean pick = br.body.has(Moves::pick);
            if (!table && !pick) return 16;
            if (!pick) return 8;
            return 4;
        }

        public boolean trigger(Brain br) {
            int wood = Moves.wood(br.body);
            if (wood >= wanted(br)) return false;
            tree = br.notebook.nearest(Notebook.Kind.LOG, br.bot().getBlockPos(), 32);
            if (tree == null) return false;
            because = "only " + wood + " wood's worth; a tree at " + tree.pos.toShortString();
            return true;
        }

        public Outcome tick(Brain br) {
            if (Moves.wood(br.body) >= wanted(br) + 4) return DONE;
            if (log == null) {
                if (tree == null || !Moves.stillThere(br, tree)) {
                    tree = br.notebook.nearest(Notebook.Kind.LOG, br.bot().getBlockPos(), 32);
                    if (tree == null) return Moves.wood(br.body) > 0 ? DONE : FAILED;
                }
                log = tree.pos;
            }
            Outcome o = mine(br, log);
            if (o == RUNNING) return RUNNING;
            br.notebook.forget(Notebook.Kind.LOG, log);
            if (o == FAILED) {
                log = null;
                tree = null;
                return RUNNING;
            }
            // The rest of the same tree, if it can reach it.
            BlockPos next = null;
            for (BlockPos p : BlockPos.iterate(log.add(-1, 0, -1), log.add(1, 2, 1))) {
                if (br.world().getBlockState(p).isIn(net.minecraft.registry.tag.BlockTags.LOGS) && br.body.inReach(p)) {
                    next = p.toImmutable();
                    break;
                }
            }
            log = next;
            if (next == null) tree = null;
            return Moves.wood(br.body) >= wanted(br) + 4 ? DONE : RUNNING;
        }
    }

    // ------------------------------------------------------------------ N2 planks

    static final class Planks extends Reaction {
        Planks() { super("N2", "Make planks", NEED, 10, false, CRAFTING); }
        public Reaction fresh() { return new Planks(); }

        private Orders.Craft c;
        private int made;

        public boolean trigger(Brain br) {
            if (br.body.count(Moves::log) == 0 || br.body.count(Moves::planks) >= 4) return false;
            because = "logs but no planks";
            return true;
        }

        public Outcome tick(Brain br) {
            if (c == null) c = new Orders.Craft(Moves::planks, "planks");
            Outcome o = craft(br, c);
            if (o != DONE) return o;
            made++;
            if (made < 2 && br.body.count(Moves::log) > 0 && br.body.count(Moves::planks) < 8) {
                c = null;
                return RUNNING;
            }
            return DONE;
        }
    }

    // ------------------------------------------------------------------ N3 table

    static final class Table extends Reaction {
        Table() { super("N3", "Make a crafting table", NEED, 15, false, CRAFTING); }
        public Reaction fresh() { return new Table(); }

        private Orders.Craft c;

        public boolean trigger(Brain br) {
            if (br.body.count(Moves::planks) < 4 || Moves.tableAt(br, 16)) return false;
            because = "4 planks and no crafting table";
            return true;
        }

        public Outcome tick(Brain br) {
            if (!br.body.has(s -> s.isOf(Items.CRAFTING_TABLE))) {
                if (c == null) c = new Orders.Craft(Items.CRAFTING_TABLE);
                Outcome o = craft(br, c);
                if (o != DONE) return o;
            }
            Body.Placed p = br.body.placeNearby(Items.CRAFTING_TABLE, Blocks.CRAFTING_TABLE);
            if (p.working()) return RUNNING;
            if (p.failed()) return FAILED;
            br.notebook.note(Notebook.Kind.TABLE, p.at(), br.now(), "my crafting table", p.at());
            return DONE;
        }
    }

    // ------------------------------------------------------------------ N4 wood tools / N6 iron tools

    /** Crafts a list of tools in turn, skipping any it has or can't make. */
    abstract static class Tools extends Reaction {
        Tools(String id, String name) { super(id, name, NEED, 30, false, CRAFTING); }

        private List<Item> todo;
        private Orders.Craft c;
        private boolean any;

        abstract List<Item> wants(Brain br);

        public Outcome tick(Brain br) {
            if (todo == null) todo = new java.util.ArrayList<>(wants(br));
            while (c == null) {
                if (todo.isEmpty()) return any ? DONE : FAILED;
                Item next = todo.remove(0);
                if (br.body.has(s -> s.isOf(next))) continue;
                if (Crafting.plan(br.bot(), next, Moves.tableAt(br, 8)) == null) continue;
                c = new Orders.Craft(next);
            }
            Outcome o = craft(br, c);
            if (o == RUNNING) return RUNNING;
            if (o == DONE) any = true;
            c = null;
            return RUNNING;
        }
    }

    static final class WoodTools extends Tools {
        WoodTools() { super("N4", "Wood tools"); }
        public Reaction fresh() { return new WoodTools(); }

        List<Item> wants(Brain br) {
            return List.of(Items.WOODEN_PICKAXE, Items.WOODEN_SWORD);
        }

        public boolean trigger(Brain br) {
            if (br.body.has(Moves::pick)) return false;
            Notebook.Mark t = br.notebook.nearest(Notebook.Kind.TABLE, br.bot().getBlockPos(), 6);
            if (t == null && !br.body.has(s -> s.isOf(Items.CRAFTING_TABLE))) return false;
            if (Moves.wood(br.body) < 5) return false;
            because = "at a crafting table with no pick";
            return true;
        }
    }

    static final class IronTools extends Tools {
        IronTools() { super("N6", "Iron tools"); }
        public Reaction fresh() { return new IronTools(); }

        List<Item> wants(Brain br) {
            return List.of(Items.IRON_PICKAXE, Items.IRON_SWORD);
        }

        public boolean trigger(Brain br) {
            if (br.body.count(Items.IRON_INGOT) < 3 || br.body.has(s -> s.isOf(Items.IRON_PICKAXE))) return false;
            if (!Moves.tableAt(br, 16)) return false;
            because = br.body.count(Items.IRON_INGOT) + " iron ingots: an iron pick";
            return true;
        }
    }

    // ------------------------------------------------------------------ N5 cobblestone

    static final class Cobble extends Reaction {
        Cobble() { super("N5", "Gather cobblestone", NEED, 60, false, GATHERING); }
        public Reaction fresh() { return new Cobble(); }

        private Notebook.Mark at;

        public boolean trigger(Brain br) {
            boolean builder = br.traits().keep() == Traits.Keep.BUILDER;
            int want = builder ? 32 : 8;
            if (!br.body.has(Moves::pick) || br.body.count(Items.COBBLESTONE) >= want || (!builder && Moves.furnaceAt(br, 32))) return false;
            at = br.notebook.nearest(Notebook.Kind.COBBLE, br.bot().getBlockPos(), 24);
            if (at == null) return false;
            because = "a furnace takes 8 cobblestone; I have " + br.body.count(Items.COBBLESTONE);
            return true;
        }

        public Outcome tick(Brain br) {
            if (br.body.count(Items.COBBLESTONE) >= (br.traits().keep() == Traits.Keep.BUILDER ? 32 : 8)) return DONE;
            if (at == null || !Moves.stillThere(br, at)) {
                at = br.notebook.nearest(Notebook.Kind.COBBLE, br.bot().getBlockPos(), 24);
                if (at == null) return br.body.count(Items.COBBLESTONE) > 0 ? DONE : FAILED;
            }
            Outcome o = mine(br, at.pos);
            if (o == RUNNING) return RUNNING;
            br.notebook.forget(at.kind, at.pos);
            at = null;
            return RUNNING;
        }
    }

    // ------------------------------------------------------------------ N7 forage

    static final class Forage extends Reaction {
        Forage() { super("N7", "Forage", NEED, 30, false, GATHERING); }
        public Reaction fresh() { return new Forage(); }

        private final Moves.Forage forage = new Moves.Forage();

        public boolean trigger(Brain br) {
            boolean glutton = br.traits().keep() == Traits.Keep.GLUTTON;   // hunts every animal it sees
            if (!glutton && (br.bot().getHungerManager().getFoodLevel() > 14 || Moves.bestFoodSlot(br.body) >= 0)) return false;
            if (glutton && !Moves.emptySlot(br.body)) return false;
            boolean prey = br.senses.visible().stream().anyMatch(Moves::prey);
            boolean leaves = br.notebook.nearest(Notebook.Kind.LEAVES, br.bot().getBlockPos(), 24) != null;
            if (!prey && !leaves) return false;
            because = "getting hungry, and no food on me";
            return true;
        }

        public Outcome tick(Brain br) {
            if (Moves.bestFoodSlot(br.body) >= 0 && br.traits().keep() != Traits.Keep.GLUTTON) return DONE;
            return forage.tick(br);
        }
    }

    // ------------------------------------------------------------------ N8 furnace

    static final class Furnace extends Reaction {
        Furnace() { super("N8", "Make a furnace", NEED, 20, false, CRAFTING); }
        public Reaction fresh() { return new Furnace(); }

        private final Orders.Craft c = new Orders.Craft(Items.FURNACE);

        public boolean trigger(Brain br) {
            if (br.body.count(Items.COBBLESTONE) < 8 || Moves.furnaceAt(br, 32) || !Moves.tableAt(br, 16)) return false;
            because = "8 cobblestone and no furnace";
            return true;
        }

        public Outcome tick(Brain br) {
            return craft(br, c);
        }
    }

    // ------------------------------------------------------------------ N9 ore

    static final class Ore extends Reaction {
        Ore() { super("N9", "Mine ore", NEED, 60, false, GATHERING); }
        public Reaction fresh() { return new Ore(); }

        private Notebook.Mark ore;
        private boolean leftover;

        static int worth(Notebook.Mark m) {
            String w = m.what.toLowerCase();
            if (w.contains("diamond")) return 0;
            if (w.contains("iron")) return 1;
            if (w.contains("coal")) return 2;
            if (w.contains("gold")) return 3;
            if (w.contains("copper")) return 4;
            return 9;
        }

        public boolean trigger(Brain br) {
            if (!br.body.has(Moves::pick)) return false;
            BlockPos at = br.bot().getBlockPos();
            Notebook.Mark best = null;
            for (Notebook.Mark m : br.notebook.all(Notebook.Kind.ORE)) {
                if (worth(m) > 4 || m.pos.getSquaredDistance(at) > 24 * 24) continue;
                if (best == null || worth(m) < worth(best)
                        || (worth(m) == worth(best) && m.pos.getSquaredDistance(at) < best.pos.getSquaredDistance(at))) best = m;
            }
            if (best == null) return false;
            ore = best;
            because = best.what + " at " + best.pos.toShortString();
            return true;
        }

        public Outcome tick(Brain br) {
            if (ore == null) return DONE;
            if (!leftover && !Moves.stillThere(br, ore)) return DONE;
            Outcome o = mine(br, ore.pos);
            if (o == RUNNING) return RUNNING;
            if (o == FAILED) {
                br.notebook.forget(ore.kind, ore.pos);
                return FAILED;
            }
            br.notebook.forget(ore.kind, ore.pos);
            // Mined ore leaves cobblestone behind: that's furnace stone.
            if (!leftover && br.world().getBlockState(ore.pos).isOf(Blocks.COBBLESTONE) && br.body.count(Items.COBBLESTONE) < 16) {
                leftover = true;
                return RUNNING;
            }
            return DONE;
        }
    }

    // ------------------------------------------------------------------ N10 smelt (and lava for fuel)

    static boolean rawOre(ItemStack s) {
        String p = Moves.id(s).getPath();
        return p.startsWith("raw_") || p.endsWith("_ore");
    }

    static final class Smelt extends Reaction {
        Smelt() { super("N10", "Smelt", NEED, 200, false, CRAFTING); }
        public Reaction fresh() { return new Smelt(); }

        private final Orders.Smelt smelt = new Orders.Smelt();

        public boolean trigger(Brain br) {
            Body b = br.body;
            if (!b.has(Needs::rawOre)) return false;
            if (!b.has(Moves::igniter)) return false;
            boolean fuel = b.has(s -> Moves.lavaBucket(s) || s.isOf(Items.COAL) || s.isOf(Items.CHARCOAL) || Moves.log(s) || Moves.planks(s));
            if (!fuel) return false;
            boolean furnace = b.has(s -> s.isOf(Items.FURNACE))
                    || br.notebook.nearest(Notebook.Kind.FURNACE, br.bot().getBlockPos(), 8) != null
                    || br.notebook.nearest(Notebook.Kind.FLAME, br.bot().getBlockPos(), 8, m -> m.what.contains("Furnace")) != null
                    || (b.count(Items.COBBLESTONE) >= 8 && Moves.tableAt(br, 8));
            if (!furnace) return false;
            because = "raw ore, fuel, a furnace and a way to light it";
            return true;
        }

        public Outcome tick(Brain br) {
            if (!smelt.tick(br.body)) return RUNNING;
            return br.body.lastSaid.startsWith("done") ? DONE : FAILED;
        }
    }

    /** No way to light a furnace: fill a bucket at lava and burn that (lava lights the furnace itself). */
    static final class LavaFuel extends Reaction {
        LavaFuel() { super("N10b", "Lava for the furnace", NEED, 60, false, CRAFTING); }
        public Reaction fresh() { return new LavaFuel(); }

        private Notebook.Mark lava;
        private Orders.Craft c;
        private int tries;

        public boolean trigger(Brain br) {
            Body b = br.body;
            if (!b.has(Needs::rawOre) || b.has(Moves::igniter)) return false;
            if (!b.has(s -> s.isOf(Items.FURNACE)) && b.count(Items.COBBLESTONE) < 8) return false;
            if (!b.has(Moves::emptyBucket) && b.count(Moves::planks) < 3) return false;
            lava = br.notebook.nearest(Notebook.Kind.LAVA, br.bot().getBlockPos(), 32);
            if (lava == null) return false;
            because = "nothing to light a furnace with, but lava at " + lava.pos.toShortString();
            return true;
        }

        public Outcome tick(Brain br) {
            Body b = br.body;
            if (b.has(Moves::lavaBucket)) return DONE;
            if (!b.has(Moves::emptyBucket)) {
                if (c == null) c = new Orders.Craft(Registries.ITEM.get(new Identifier("unbeta-content", "wood_bucket")));
                Outcome o = craft(br, c);
                return o == FAILED ? FAILED : RUNNING;
            }
            if (!Moves.stillThere(br, lava)) return FAILED;
            if (b.bot.getEyePos().distanceTo(Vec3d.ofCenter(lava.pos)) > 4) {
                return b.walkTo(lava.pos, 3.0) == Body.Walk.FAILED ? FAILED : RUNNING;
            }
            b.stopMoving();
            b.hold(Moves::emptyBucket);
            if (b.lookAt(Vec3d.ofCenter(lava.pos).add(0, 0.4, 0))) {
                b.useHeld();
                if (++tries > 6) return FAILED;
            }
            return RUNNING;
        }
    }

    // ------------------------------------------------------------------ N11 torches / N12 light them

    static final class Torches extends Reaction {
        Torches() { super("N11", "Make torches", NEED, 10, false, CRAFTING); }
        public Reaction fresh() { return new Torches(); }

        private final Orders.Craft c = new Orders.Craft(Moves::unlitTorch, "torches");

        public boolean trigger(Brain br) {
            Body b = br.body;
            if (b.count(s -> s.isOf(Items.COAL) || s.isOf(Items.CHARCOAL)) == 0) return false;
            if (b.count(s -> s.isOf(Items.STICK)) == 0 && b.count(Moves::planks) == 0) return false;
            if (b.count(s -> Moves.unlitTorch(s) || Moves.litTorch(s)) >= 8) return false;
            long day = br.world().getTimeOfDay() % 24000;
            boolean duskSoon = day >= 6000 && day < 13000;
            if (!duskSoon && br.traits().keep() != Traits.Keep.LIGHTENER) return false;
            because = "coal and sticks, and dusk is coming";
            return true;
        }

        public Outcome tick(Brain br) {
            return craft(br, c);
        }
    }

    static final class LightTorches extends Reaction {
        LightTorches() { super("N12", "Light a torch", NEED, 40, false, CRAFTING); }
        public Reaction fresh() { return new LightTorches(); }

        private final Moves.LightTorch light = new Moves.LightTorch();

        public boolean trigger(Brain br) {
            Body b = br.body;
            if (!b.has(Moves::unlitTorch) || b.has(Moves::litTorch)) return false;
            long day = br.world().getTimeOfDay() % 24000;
            if (day < 11000 && br.traits().keep() != Traits.Keep.LIGHTENER) return false;
            Notebook.Mark f = br.notebook.nearest(Notebook.Kind.FLAME, b.bot.getBlockPos(), 32);
            if (f == null) return false;
            because = "an unlit torch, and " + f.what + " at " + f.pos.toShortString();
            return true;
        }

        public Outcome tick(Brain br) {
            return light.tick(br);
        }
    }

    // ------------------------------------------------------------------ N13 water

    static final class Water extends Reaction {
        Water() { super("N13", "Fetch water", NEED, 45, false, CRAFTING); }
        public Reaction fresh() { return new Water(); }

        private Notebook.Mark water;
        private Orders.Craft c;
        private int tries;

        public boolean trigger(Brain br) {
            Traits.Keep k = br.traits().keep();
            if (k != Traits.Keep.BUCKETEER && k != Traits.Keep.SURVIVOR && k != Traits.Keep.LIGHTENER) return false;
            Body b = br.body;
            if (b.has(Moves::waterBucket)) return false;
            if (!b.has(Moves::emptyBucket) && b.count(Moves::planks) < 3) return false;
            water = br.notebook.nearest(Notebook.Kind.WATER, b.bot.getBlockPos(), 32);
            if (water == null) return false;
            because = "I like having water on me";
            return true;
        }

        public Outcome tick(Brain br) {
            Body b = br.body;
            if (b.has(Moves::waterBucket)) return DONE;
            if (!b.has(Moves::emptyBucket)) {
                if (c == null) c = new Orders.Craft(Registries.ITEM.get(new Identifier("unbeta-content", "wood_bucket")));
                Outcome o = craft(br, c);
                return o == FAILED ? FAILED : RUNNING;
            }
            if (!Moves.stillThere(br, water)) return FAILED;
            if (b.bot.getEyePos().distanceTo(Vec3d.ofCenter(water.pos)) > 4) {
                return b.walkTo(water.pos, 2.5) == Body.Walk.FAILED ? FAILED : RUNNING;
            }
            b.stopMoving();
            b.hold(Moves::emptyBucket);
            if (b.lookAt(Vec3d.ofCenter(water.pos).add(0, 0.4, 0))) {
                b.useHeld();
                if (++tries > 6) return FAILED;
            }
            return RUNNING;
        }
    }

    // ------------------------------------------------------------------ N14 worn tool

    static final class WornTool extends Reaction {
        WornTool() { super("N14", "Replace a worn tool", NEED, 20, false, CRAFTING); }
        public Reaction fresh() { return new WornTool(); }

        private Orders.Craft c;

        public boolean trigger(Brain br) {
            Body b = br.body;
            for (int i = 0; i < 36; i++) {
                ItemStack s = b.bot.getInventory().getStack(i);
                if (!(s.getItem() instanceof PickaxeItem || s.getItem() instanceof SwordItem)) continue;
                if (s.getMaxDamage() <= 0 || s.getDamage() < s.getMaxDamage() * 0.9) continue;
                Item item = s.getItem();
                if (b.count(item) > 1) continue;
                if (Crafting.plan(b.bot, item, Moves.tableAt(br, 8)) == null) continue;
                c = new Orders.Craft(item);
                because = s.getName().getString() + " is nearly worn out";
                return true;
            }
            return false;
        }

        public Outcome tick(Brain br) {
            return craft(br, c);
        }
    }

    // ------------------------------------------------------------------ N15 full

    static final class Full extends Reaction {
        Full() { super("N15", "Inventory full", NEED, 3, false); }
        public Reaction fresh() { return new Full(); }

        static int score(ItemStack s) {
            if (s.isEmpty()) return 99;
            if (s.isOf(Items.DIRT) || s.isOf(Items.GRAVEL) || s.isOf(Items.SAND) || s.isOf(Items.ROTTEN_FLESH)
                    || s.isOf(Items.WHEAT_SEEDS) || s.isOf(Items.FLOWERING_AZALEA) || s.isOf(Items.POPPY) || s.isOf(Items.DANDELION)) return 0;
            if (s.isOf(Items.COBBLESTONE)) return 1;
            if (s.isIn(net.minecraft.registry.tag.ItemTags.SAPLINGS)) return 1;
            return 5;
        }

        public boolean trigger(Brain br) {
            if (Moves.emptySlot(br.body)) return false;
            because = "my pockets are full";
            return true;
        }

        public Outcome tick(Brain br) {
            Body b = br.body;
            int worst = -1, worstScore = 99;
            for (int i = 0; i < 36; i++) {
                int sc = score(b.bot.getInventory().getStack(i));
                if (sc < worstScore) {
                    worstScore = sc;
                    worst = i;
                }
            }
            if (worst < 0 || worstScore >= 5) return FAILED;
            b.throwSlot(worst, 0.3);
            return DONE;
        }
    }

    // ------------------------------------------------------------------ N17 bed

    static final class Bed extends Reaction {
        Bed() { super("N17", "Sleep in a bed", NEED, 20, false, CRAFTING); }
        public Reaction fresh() { return new Bed(); }

        private BlockPos bed;
        private int tries;

        public boolean trigger(Brain br) {
            if (br.traits().keep() != Traits.Keep.HOMESTEADER && br.win() != Traits.Win.HIGHER) return false;
            if (!br.world().isNight() || !br.body.has(s -> s.getItem() instanceof BedItem)) return false;
            if (br.nearestFoe(16) != null) return false;
            because = "night, and I have a bed";
            return true;
        }

        public Outcome tick(Brain br) {
            Body b = br.body;
            DummyPlayer bot = b.bot;
            if (bot.isSleeping()) return DONE;
            if (bed == null) {
                int slot = b.slot(s -> s.getItem() instanceof BedItem);
                if (slot < 0) return FAILED;
                Item item = bot.getInventory().getStack(slot).getItem();
                Body.Placed p = b.placeNearby(item, ((BedItem) item).getBlock());
                if (p.working()) return RUNNING;
                if (p.failed()) return FAILED;
                bed = p.at();
            }
            b.lookAt(Vec3d.ofCenter(bed));
            if (tries++ % 10 == 0) b.useOn(bed);
            return tries > 60 ? FAILED : RUNNING;
        }
    }

    // ------------------------------------------------------------------ N18 armour

    static final class Armour extends Reaction {
        Armour() { super("N18", "Make armour", NEED, 30, false, CRAFTING); }
        public Reaction fresh() { return new Armour(); }

        private static final Item[][] SETS = {
                {Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_HELMET, Items.DIAMOND_BOOTS},
                {Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_HELMET, Items.IRON_BOOTS},
                {Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_HELMET, Items.GOLDEN_BOOTS},
                {Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_HELMET, Items.LEATHER_BOOTS}};

        private Item piece;
        private Orders.Craft c;

        static int protection(ItemStack s) {
            return s.getItem() instanceof ArmorItem a ? a.getProtection() : 0;
        }

        public boolean trigger(Brain br) {
            Body b = br.body;
            // Working out recipes is costly: only every 5 seconds.
            if (br.now() % 100 >= BotConfig.get().thinkTicks) return false;
            int mats = b.count(Items.IRON_INGOT) + b.count(Items.GOLD_INGOT) + b.count(Items.DIAMOND) + b.count(Items.LEATHER);
            if (mats < 4 || !Moves.tableAt(br, 16)) return false;
            for (Item[] set : SETS) {
                for (Item it : set) {
                    ArmorItem a = (ArmorItem) it;
                    EquipmentSlot slot = a.getSlotType();
                    if (protection(b.bot.getEquippedStack(slot)) >= a.getProtection()) continue;
                    if (Crafting.plan(b.bot, it, true) == null) continue;
                    piece = it;
                    because = "I can make " + it.getName().getString();
                    return true;
                }
            }
            return false;
        }

        public Outcome tick(Brain br) {
            Body b = br.body;
            if (c == null) c = new Orders.Craft(piece);
            Outcome o = craft(br, c);
            if (o != DONE) return o;
            // Put it on: using armour in hand wears it.
            if (b.hold(s -> s.isOf(piece))) b.useHeld();
            return DONE;
        }
    }
}
