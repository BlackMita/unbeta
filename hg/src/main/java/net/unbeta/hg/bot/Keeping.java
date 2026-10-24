package net.unbeta.hg.bot;

import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.BedItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.unbeta.hg.DummyPlayer;
import net.unbeta.hg.Keeps;
import net.unbeta.hg.Round;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static net.unbeta.hg.bot.Reaction.Outcome.DONE;
import static net.unbeta.hg.bot.Reaction.Outcome.FAILED;
import static net.unbeta.hg.bot.Reaction.Outcome.RUNNING;

/**
 * Keep Methods (design doc, B): a bot's taste. What it wants kept, in order; how it scores items
 * (for choosing keeps and for looting); what its gathering chases next (N19); and when it buys,
 * which is its Win Method's call.
 */
final class Keeping {

    private Keeping() {}

    /** One line of a keep list: what counts, and what to buy when it holds nothing of the kind (the lobby). */
    record Entry(String label, Predicate<ItemStack> is, Item fallback) {}

    static Item item(String id) {
        return Registries.ITEM.get(new Identifier(id));
    }

    static Entry of(Item i) {
        return new Entry(i.getName().getString(), s -> s.isOf(i), i);
    }

    static Entry of(String id) {
        return of(item(id));
    }

    static Entry best(String label, Predicate<ItemStack> is, Item fallback) {
        return new Entry(label, is, fallback);
    }

    static boolean cookedFood(ItemStack s) {
        return s.isOf(Items.COOKED_BEEF) || s.isOf(Items.COOKED_PORKCHOP) || s.isOf(Items.COOKED_CHICKEN)
                || s.isOf(Items.COOKED_MUTTON) || s.isOf(Items.COOKED_RABBIT) || s.isOf(Items.BREAD) || s.isOf(Items.COOKED_COD);
    }

    /** Its keep list, in order. Empty for the wild cards, who choose differently. */
    static List<Entry> list(Traits.Keep k) {
        return switch (k) {
            case WOODSMAN -> List.of(of(Items.WOODEN_SWORD), of(Items.WOODEN_AXE), of("unbeta-content:wood_bucket"), best("planks", Moves::planks, Items.OAK_PLANKS));
            case FLETCHER -> List.of(of(Items.ARROW), of("unbeta-content:heavens_arrow"), of(Items.BOW));
            case IRONCLAD -> List.of(of(Items.IRON_SWORD), of(Items.IRON_CHESTPLATE), of(Items.IRON_PICKAXE), of(Items.BUCKET));
            case COPPERHEAD -> List.of(of("unbeta-content:copper_bucket"), of(Items.ARROW), of("unbeta-content:copper_wire"));
            case GILDED -> List.of(of(Items.GOLDEN_SWORD), of(Items.GOLDEN_PICKAXE), of(Items.GOLDEN_APPLE), of(Items.GOLDEN_CHESTPLATE));
            case SURVIVOR -> List.of(best("a wood tool", s -> Moves.id(s).getPath().startsWith("wooden_"), Items.WOODEN_PICKAXE),
                    best("planks", Moves::planks, Items.OAK_PLANKS), best("food", Moves::food, Items.BREAD));
            case LIGHTENER -> List.of(of("unbeta-content:torch"), of("unbeta-content:jack_o_lantern"), of("unbeta-content:glowsand"), of(Items.FLINT_AND_STEEL));
            case MEDIC -> List.of(of(Items.GOLDEN_APPLE), of(Items.MILK_BUCKET), best("cooked food", Keeping::cookedFood, Items.COOKED_BEEF));
            case GLUTTON -> List.of(best("cooked food", Keeping::cookedFood, Items.COOKED_BEEF), best("food", Moves::food, Items.BREAD));
            case BULWARK -> List.of(best("a chestplate", s -> Moves.id(s).getPath().endsWith("_chestplate"), Items.LEATHER_CHESTPLATE),
                    best("leggings", s -> Moves.id(s).getPath().endsWith("_leggings"), Items.LEATHER_LEGGINGS),
                    best("a helmet", s -> Moves.id(s).getPath().endsWith("_helmet"), Items.LEATHER_HELMET),
                    best("boots", s -> Moves.id(s).getPath().endsWith("_boots"), Items.LEATHER_BOOTS));
            case DUELIST -> List.of(best("its best sword", s -> s.getItem() instanceof net.minecraft.item.SwordItem, Items.WOODEN_SWORD), of(Items.BOW), of(Items.ARROW));
            case BUILDER -> List.of(of(Items.COBBLESTONE), best("planks", Moves::planks, Items.OAK_PLANKS), of(Items.LADDER), of(Items.OAK_DOOR));
            case HOMESTEADER -> List.of(best("a bed", s -> s.getItem() instanceof BedItem, Items.RED_BED), of(Items.CHEST), of(Items.CRAFTING_TABLE));
            case BUCKETEER -> List.of(best("a water bucket", Moves::waterBucket, Items.WATER_BUCKET), best("a lava bucket", Moves::lavaBucket, Items.LAVA_BUCKET));
            case SPELUNKER -> List.of(best("its best pick", Moves::pick, Items.WOODEN_PICKAXE), of("unbeta-content:torch"));
            case PEARL_DIVER -> List.of(of("unbeta-content:unbeta_pearl"));
            case BOMBER -> List.of(of(Items.TNT), of(Items.FLINT_AND_STEEL));
            default -> List.of();
        };
    }

    static boolean onList(Brain br, ItemStack s) {
        for (Entry e : list(br.traits().keep())) if (e.is().test(s)) return true;
        return false;
    }

    // ------------------------------------------------------------------ scoring

    /** Material rank: wood 1, copper 2, iron 3, gold 3, diamond 5; other things 1; junk 0. */
    static double material(ItemStack s) {
        String p = Moves.id(s).getPath();
        if (p.startsWith("wooden_") || p.startsWith("wood_")) return 1;
        if (p.contains("copper")) return 2;
        if (p.startsWith("iron_") || p.equals("bucket")) return 3;
        if (p.startsWith("golden_") || p.startsWith("gold_")) return 3;
        if (p.startsWith("diamond")) return 5;
        return 1;
    }

    static boolean junk(Brain br, ItemStack s) {
        if (s.isOf(Items.COBBLESTONE)) return br.traits().keep() != Traits.Keep.BUILDER && br.body.count(Items.COBBLESTONE) > 16;
        return s.isOf(Items.DIRT) || s.isOf(Items.GRAVEL) || s.isOf(Items.SAND) || s.isOf(Items.ROTTEN_FLESH)
                || s.isOf(Items.WHEAT_SEEDS) || s.isOf(Items.POPPY) || s.isOf(Items.DANDELION) || s.isIn(net.minecraft.registry.tag.ItemTags.SAPLINGS);
    }

    /** Item score: material rank, x3 on its keep list, x2 a weapon or armour while it has none. Under 1 is junk. */
    static double score(Brain br, ItemStack s) {
        if (s.isEmpty() || junk(br, s)) return 0;
        double v = material(s);
        if (onList(br, s)) v *= 3;
        Body b = br.body;
        if (Moves.weapon(s) && !b.has(Moves::weapon)) v *= 2;
        if (s.getItem() instanceof ArmorItem a && b.bot.getEquippedStack(a.getSlotType()).isEmpty()) v *= 2;
        return v;
    }

    /** Takes from a chest what it values most, until its pockets are full. */
    static void takeBest(Brain br, Inventory chest) {
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < chest.size(); i++) if (!chest.getStack(i).isEmpty()) slots.add(i);
        slots.sort((a, b) -> Double.compare(score(br, chest.getStack(b)), score(br, chest.getStack(a))));
        for (int i : slots) {
            ItemStack s = chest.getStack(i);
            if (score(br, s) < 1 && br.traits().keep() != Traits.Keep.PACK_RAT) continue;
            ItemStack moving = s.copy();
            br.bot().getInventory().insertStack(moving);
            chest.setStack(i, moving);
            if (!moving.isEmpty()) break;   // full
        }
        chest.markDirty();
    }

    // ------------------------------------------------------------------ buying keeps

    /** Every few seconds: buys a keep if its Win Method says now, with what its Keep Method wants. */
    static void tickBuying(Brain br) {
        DummyPlayer bot = br.bot();
        Traits.Keep k = br.traits().keep();
        if (k == Traits.Keep.BANKER || bot.experienceLevel < Keeps.cost(Keeps.owned(bot))) return;
        Round.Phase phase = Round.phase();
        Item pick = null;
        if (phase == Round.Phase.LOBBY) {
            if (br.win() == Traits.Win.MIDDLING) return;   // waits
            List<Entry> l = list(k);
            if (!l.isEmpty()) pick = l.get(0).fallback();
        } else if (phase == Round.Phase.GRACE || phase == Round.Phase.GAME) {
            switch (k) {
                case PACK_RAT -> pick = mostHeld(br);
                case COPYCAT -> pick = mostSeen(br);
                case GAMBLER -> {
                    pick = randomHeld(br);
                    if (pick != null) br.chat("let's see");
                }
                default -> {
                    switch (br.win()) {
                        case LOWER -> pick = firstHeld(br);
                        case MIDDLING -> {
                            if (filled(br) >= BotConfig.get().middlingBuyAtSlots) pick = bestHeld(br);
                        }
                        default -> pick = bestHeld(br);
                    }
                }
            }
        }
        if (pick == null || pick == Items.AIR || Keeps.kept(bot, pick)) return;
        Keeps.buy(bot, pick);
    }

    static int filled(Brain br) {
        int n = 0;
        for (int i = 0; i < 36; i++) if (!br.bot().getInventory().getStack(i).isEmpty()) n++;
        return n;
    }

    private static Item firstHeld(Brain br) {
        for (Entry e : list(br.traits().keep())) {
            int slot = br.body.slot(s -> e.is().test(s) && !Keeps.kept(br.bot(), s.getItem()));
            if (slot >= 0) return br.bot().getInventory().getStack(slot).getItem();
        }
        return null;
    }

    private static Item bestHeld(Brain br) {
        Item best = null;
        double bestV = 1;
        for (int i = 0; i < 36; i++) {
            ItemStack s = br.bot().getInventory().getStack(i);
            if (s.isEmpty() || Keeps.kept(br.bot(), s.getItem())) continue;
            double v = score(br, s);
            if (v > bestV) {
                bestV = v;
                best = s.getItem();
            }
        }
        return best;
    }

    private static Item mostHeld(Brain br) {
        Map<Item, Integer> counts = new HashMap<>();
        for (int i = 0; i < 36; i++) {
            ItemStack s = br.bot().getInventory().getStack(i);
            if (!s.isEmpty() && !Keeps.kept(br.bot(), s.getItem())) counts.merge(s.getItem(), s.getCount(), Integer::sum);
        }
        return counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }

    private static Item mostSeen(Brain br) {
        return br.notebook.handsSeen.entrySet().stream().filter(e -> !Keeps.kept(br.bot(), e.getKey()))
                .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }

    private static Item randomHeld(Brain br) {
        List<Item> held = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            ItemStack s = br.bot().getInventory().getStack(i);
            if (!s.isEmpty() && !Keeps.kept(br.bot(), s.getItem())) held.add(s.getItem());
        }
        return held.isEmpty() ? null : held.get(br.random.nextInt(held.size()));
    }

    // ------------------------------------------------------------------ N19: chasing its next goal

    /** What its Keep Method wants made next, in order (the gathering it steers toward). */
    static List<Entry> goals(Traits.Keep k) {
        return switch (k) {
            case WOODSMAN -> List.of(of(Items.WOODEN_AXE), of(Items.WOODEN_SWORD), of("unbeta-content:wood_bucket"));
            case FLETCHER, DUELIST -> List.of(of(Items.BOW), of(Items.ARROW));
            case IRONCLAD -> List.of(of(Items.IRON_PICKAXE), of(Items.IRON_SWORD), of(Items.IRON_CHESTPLATE), of(Items.BUCKET));
            case COPPERHEAD -> List.of(of("unbeta-content:copper_bucket"), of(Items.ARROW));
            case GILDED -> List.of(of(Items.GOLDEN_SWORD), of(Items.GOLDEN_PICKAXE), of(Items.GOLDEN_APPLE), of(Items.GOLDEN_CHESTPLATE));
            case LIGHTENER -> List.of(of("unbeta-content:torch"), of(Items.FLINT_AND_STEEL));
            case MEDIC -> List.of(of(Items.GOLDEN_APPLE), of(Items.BUCKET));
            case BULWARK -> List.of(of(Items.IRON_CHESTPLATE), of(Items.IRON_LEGGINGS), of(Items.IRON_HELMET), of(Items.IRON_BOOTS));
            case BUILDER -> List.of(of(Items.LADDER), of(Items.OAK_DOOR));
            case HOMESTEADER -> List.of(best("a bed", s -> s.getItem() instanceof BedItem, Items.RED_BED), of(Items.CHEST));
            case BUCKETEER -> List.of(of(Items.BUCKET), of("unbeta-content:copper_bucket"));
            case SPELUNKER -> List.of(of(Items.IRON_PICKAXE), of("unbeta-content:torch"));
            case BOMBER -> List.of(of(Items.TNT), of(Items.FLINT_AND_STEEL));
            default -> List.of();
        };
    }

    static final class Goal extends Reaction {
        Goal() { super("N19", "Keep Method goal", Tier.NEED, 60, false, Tag.GATHERING); }
        public Reaction fresh() { return new Goal(); }

        private Entry want;
        private Orders.Craft craft;
        private Notebook.Mark mark;
        private EntityType<?> preyType;
        private LivingEntity prey;

        public boolean trigger(Brain br) {
            if (br.now() % 100 >= BotConfig.get().thinkTicks) return false;   // recipes are costly: every 5 s
            Body b = br.body;
            for (Entry e : goals(br.traits().keep())) {
                if (b.has(e.is())) continue;
                want = e;
                if (Crafting.plan(b.bot, e.is(), Moves.tableAt(br, 8)) != null) {
                    because = "my Keep Method wants " + e.label() + ", and I can make it";
                    return true;
                }
                if (findNeed(br, e.label().toLowerCase())) {
                    because = "my Keep Method wants " + e.label() + ": fetching what it takes";
                    return true;
                }
                return false;   // only the next goal counts
            }
            return false;
        }

        /** Where the makings are: ore of the right metal, trees, gravel for flint, sheep for wool, chickens for feathers. */
        private boolean findNeed(Brain br, String label) {
            BlockPos at = br.bot().getBlockPos();
            String metal = label.contains("iron") || label.equals("bucket") ? "Iron" : label.contains("gold") ? "Gold"
                    : label.contains("copper") ? "Copper" : label.contains("torch") ? "Coal" : null;
            if (label.contains("flint and steel")) metal = br.body.count(Items.IRON_INGOT) == 0 ? "Iron" : null;
            if (metal != null) {
                if (!br.body.has(Moves::pick)) return false;
                String m = metal;
                mark = br.notebook.nearest(Notebook.Kind.ORE, at, 48, x -> x.what.contains(m));
                return mark != null;
            }
            if (label.contains("bed")) return findPrey(br, EntityType.SHEEP);
            if (label.contains("arrow")) {
                if (br.body.count(Items.FLINT) == 0 && br.body.count(s -> Moves.id(s).getPath().endsWith("_nugget")) == 0) {
                    mark = br.notebook.nearest(Notebook.Kind.GRAVEL, at, 32);
                    if (mark != null) return true;
                }
                return br.body.count(Items.FEATHER) == 0 && findPrey(br, EntityType.CHICKEN);
            }
            if (label.contains("wood") || label.contains("ladder") || label.contains("door") || label.contains("chest")) {
                mark = br.notebook.nearest(Notebook.Kind.LOG, at, 48);
                return mark != null;
            }
            return false;
        }

        private boolean findPrey(Brain br, EntityType<?> type) {
            for (Entity e : br.senses.visible()) {
                if (e.getType() == type && e instanceof LivingEntity le && !le.isDead()) {
                    preyType = type;
                    prey = le;
                    return true;
                }
            }
            return false;
        }

        public Outcome tick(Brain br) {
            if (br.body.has(want.is())) return DONE;
            if (prey != null) {
                if (Fight.melee(br, prey)) {
                    prey = null;
                    return DONE;
                }
                return RUNNING;
            }
            if (mark != null) {
                Outcome o = Needs.mine(br, mark.pos);
                if (o == RUNNING) return RUNNING;
                br.notebook.forget(mark.kind, mark.pos);
                return o;
            }
            if (craft == null) craft = new Orders.Craft(want.is(), want.label());
            return Needs.craft(br, craft);
        }
    }

    // ------------------------------------------------------------------ Tortoise: stash and regear

    static final class Stash extends Reaction {
        Stash() { super("N20", "Stash", Tier.NEED, 30, false, Tag.CRAFTING); }
        public Reaction fresh() { return new Stash(); }

        private Orders.Craft craft;

        public boolean trigger(Brain br) {
            if (br.traits().play() != Traits.Play.TORTOISE || br.notebook.stash != null) return false;
            if (filled(br) < 27) return false;
            if (!br.body.has(s -> s.isOf(Items.CHEST)) && (br.body.count(Moves::planks) < 8 || !Moves.tableAt(br, 8))) return false;
            because = "three-quarters full: time for a stash";
            return true;
        }

        public Outcome tick(Brain br) {
            Body b = br.body;
            if (!b.has(s -> s.isOf(Items.CHEST))) {
                if (craft == null) craft = new Orders.Craft(Items.CHEST);
                Outcome o = Needs.craft(br, craft);
                return o == FAILED ? FAILED : RUNNING;
            }
            Body.Placed p = b.placeNearby(Items.CHEST, Blocks.CHEST);
            if (p.working()) return RUNNING;
            if (p.failed()) return FAILED;
            if (br.world().getBlockEntity(p.at()) instanceof Inventory chest) {
                // Half its things go in: the ones it values least, never its weapon, tools or food.
                List<Integer> slots = new ArrayList<>();
                for (int i = 0; i < 36; i++) {
                    ItemStack s = b.bot.getInventory().getStack(i);
                    if (!s.isEmpty() && !Moves.weapon(s) && !Moves.pick(s) && !Moves.food(s)) slots.add(i);
                }
                slots.sort((x, y) -> Double.compare(score(br, b.bot.getInventory().getStack(x)), score(br, b.bot.getInventory().getStack(y))));
                int put = 0;
                for (int i = 0; i < slots.size() / 2 && put < chest.size(); i++) {
                    int slot = slots.get(i);
                    chest.setStack(put++, b.bot.getInventory().getStack(slot).copy());
                    b.bot.getInventory().setStack(slot, ItemStack.EMPTY);
                }
                chest.markDirty();
            }
            br.notebook.stash = p.at();
            br.notebook.note(Notebook.Kind.CHEST, p.at(), br.now(), "my stash", p.at());
            br.notebook.opened.add(p.at());
            return DONE;
        }
    }

    static final class Regear extends Reaction {
        Regear() { super("N21", "Regear at my stash", Tier.NEED, 90, false, Tag.LOOTING); }
        public Reaction fresh() { return new Regear(); }

        private int t;

        public boolean trigger(Brain br) {
            if (br.notebook.stash == null || br.regeared || br.now() - br.respawnedAt > 2400) return false;
            if (br.notebook.stash.getSquaredDistance(br.bot().getBlockPos()) > 300 * 300) return false;
            because = "died: back to my stash";
            return true;
        }

        public Outcome tick(Brain br) {
            Body b = br.body;
            BlockPos stash = br.notebook.stash;
            if (!b.inReach(stash)) return b.walkTo(stash, 2.5) == Body.Walk.FAILED ? FAILED : RUNNING;
            b.stopMoving();
            b.lookAt(Vec3d.ofCenter(stash));
            if (t++ == 0) {
                b.useOn(stash);
                return RUNNING;
            }
            if (t < 20) return RUNNING;
            br.regeared = true;
            if (br.world().getBlockEntity(stash) instanceof Inventory chest) {
                for (int i = 0; i < chest.size(); i++) {
                    ItemStack s = chest.getStack(i).copy();
                    if (s.isEmpty()) continue;
                    b.bot.getInventory().insertStack(s);
                    chest.setStack(i, s);
                }
                chest.markDirty();
            } else {
                br.notebook.stash = null;   // it's gone
            }
            if (b.bot.currentScreenHandler != b.bot.playerScreenHandler) b.bot.closeHandledScreen();
            return DONE;
        }
    }
}
