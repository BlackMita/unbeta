package net.unbeta.hg.bot;

import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.item.BoatItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.unbeta.content.jackolantern.JackOLanternItems;
import net.unbeta.content.light.HeldLight;
import net.unbeta.content.torch.TorchItems;
import net.unbeta.hg.DummyPlayer;

import java.util.ArrayList;
import java.util.List;

import static net.unbeta.hg.bot.Reaction.Outcome.DONE;
import static net.unbeta.hg.bot.Reaction.Outcome.FAILED;
import static net.unbeta.hg.bot.Reaction.Outcome.RUNNING;
import static net.unbeta.hg.bot.Reaction.Tag.CRAFTING;
import static net.unbeta.hg.bot.Reaction.Tag.GATHERING;
import static net.unbeta.hg.bot.Reaction.Tier.NEED;

/**
 * Unbeta know-how (Need tier): the tricks a seasoned Unbeta player uses to climb from wood to iron
 * and keep the lights on. Starting a fire by putting planks beside lava; lighting a furnace with
 * a bucket of lava; torch juggling; flint from gravel; shears and Jack o'Lanterns; charcoal;
 * crossing water by boat; going down through caves and along old rails to find ore; and, with an
 * iron pick, digging a staircase down and strip-mining.
 */
final class Know {

    private Know() {}

    // ------------------------------------------------------------------ shared judgements

    static boolean litLight(ItemStack s) {
        return HeldLight.isLit(s);
    }

    static boolean unlitLight(ItemStack s) {
        return HeldLight.isUnlit(s);
    }

    static boolean hasLitLight(Body b) {
        return b.has(Know::litLight) || HeldLight.isLit(b.bot.getOffHandStack());
    }

    static boolean jackOLantern(ItemStack s) {
        return JackOLanternItems.isUnlit(s) || JackOLanternItems.isLit(s);
    }

    static boolean anyPick(Body b, Item pick) {
        return b.has(s -> s.isOf(pick));
    }

    static boolean ironPick(Body b) {
        return anyPick(b, Items.IRON_PICKAXE) || anyPick(b, Items.DIAMOND_PICKAXE);
    }

    /**
     * It needs a flame and knows of none: an unlit light and nothing lit, or ore to smelt and
     * nothing to light the furnace with.
     */
    static boolean needsFlame(Brain br) {
        Body b = br.body;
        if (br.notebook.nearest(Notebook.Kind.FLAME, b.bot.getBlockPos(), 64) != null) return false;
        boolean lit = hasLitLight(b);
        boolean wantsLight = b.has(Know::unlitLight) && !lit;
        boolean wantsIgniter = b.has(Needs::rawOre) && !b.has(Moves::igniter) && !lit;
        return wantsLight || wantsIgniter;
    }

    static boolean burnable(ItemStack s) {
        return Moves.planks(s) || Moves.log(s);
    }

    /** Fire, or a burning Burnt block, near a point. */
    static BlockPos findFire(Brain br, BlockPos centre, int r) {
        for (BlockPos p : BlockPos.iterate(centre.add(-r, -2, -r), centre.add(r, 4, r))) {
            BlockState st = br.world().getBlockState(p);
            if (st.isIn(BlockTags.FIRE) || (Senses.isFlame(st) && !(st.getBlock() instanceof AbstractFurnaceBlock))) return p.toImmutable();
        }
        return null;
    }

    /** Is it underground (no sky above it at all)? */
    static boolean underground(Brain br) {
        return br.world().getLightLevel(net.minecraft.world.LightType.SKY, br.bot().getBlockPos()) == 0;
    }

    // ------------------------------------------------------------------ N22 fire at lava

    /**
     * No flame anywhere it knows, but lava: it sets planks on the ground beside the lava and waits
     * for the heat to catch them, then lights its torch at the fire.
     */
    static final class LavaFire extends Reaction {
        LavaFire() { super("N22", "Fire at lava", NEED, 180, false, CRAFTING); }
        public Reaction fresh() { return new LavaFire(); }

        private Notebook.Mark lava;
        private final List<BlockPos> placed = new ArrayList<>();
        private BlockPos fire;
        private int waited;
        private int fumbles;
        private final Moves.LightTorch light = new Moves.LightTorch();

        public boolean trigger(Brain br) {
            if (!needsFlame(br) || br.body.count(Know::burnable) < 2) return false;
            lava = br.notebook.nearest(Notebook.Kind.LAVA, br.bot().getBlockPos(), 48);
            if (lava == null) return false;
            because = "no flame anywhere, but lava at " + lava.pos.toShortString() + ": planks beside it will catch";
            return true;
        }

        public Outcome tick(Brain br) {
            Body b = br.body;
            if (fire != null) {
                if (hasLitLight(b) || !b.has(Know::unlitLight)) return DONE;
                return light.tick(br) == FAILED ? FAILED : RUNNING;
            }
            if (waited % 10 == 0) {
                BlockPos f = findFire(br, lava.pos, 5);
                if (f != null) {
                    fire = f;
                    br.notebook.note(Notebook.Kind.FLAME, f, br.now(), "fire", b.bot.getBlockPos());
                    br.chat(br.pickLine("fire!", "finally, fire", "it caught"));
                    return RUNNING;
                }
            }
            if (b.bot.getEyePos().distanceTo(Vec3d.ofCenter(lava.pos)) > 5) {
                return b.walkTo(lava.pos, 3.5) == Body.Walk.FAILED ? FAILED : RUNNING;
            }
            if (placed.size() < 3) {
                BlockPos spot = spot(br);
                if (spot != null && b.hold(Know::burnable) && b.placeAt(spot)) {
                    placed.add(spot);
                    return RUNNING;
                }
                if (placed.isEmpty() && ++fumbles > 40) return FAILED;   // nowhere to put them
            }
            // Wait a step back, watching for flames.
            waited++;
            b.stopMoving();
            b.lookAt(Vec3d.ofCenter(lava.pos));
            if (b.bot.isOnFire() || b.bot.isInLava()) b.flee(Vec3d.ofCenter(lava.pos));
            return RUNNING;
        }

        /** A spot on solid ground, level with the air just above some lava, that it can reach. */
        private BlockPos spot(Brain br) {
            for (BlockPos l : BlockPos.iterate(lava.pos.add(-4, -1, -4), lava.pos.add(4, 1, 4))) {
                if (!br.world().getFluidState(l).isIn(FluidTags.LAVA)) continue;
                BlockPos above = l.up();
                if (!br.world().isAir(above)) continue;
                for (Direction d : Direction.Type.HORIZONTAL) {
                    BlockPos s = above.offset(d);
                    if (placed.contains(s) || !br.world().getBlockState(s).isReplaceable() || !br.world().getFluidState(s).isEmpty()) continue;
                    BlockPos under = s.down();
                    if (!br.world().getBlockState(under).isSideSolidFullSquare(br.world(), under, Direction.UP)) continue;
                    if (!br.body.inReach(s)) continue;
                    return s.toImmutable();
                }
            }
            return null;
        }
    }

    // ------------------------------------------------------------------ N23 lava-lit furnace

    /**
     * No way to light a furnace: it sets one down beside lava, fills a bucket there and pours it
     * straight in as fuel. Lava is the flame, so the furnace lights itself, cooks its ore, and is
     * a flame to light torches at.
     */
    static final class LavaFurnace extends Reaction {
        LavaFurnace() { super("N23", "Lava-lit furnace", NEED, 300, false, CRAFTING); }
        public Reaction fresh() { return new LavaFurnace(); }

        private Notebook.Mark lava;
        private Orders.Craft craft;
        private BlockPos furnace;
        private int stage;
        private int t;
        private int limit = 200;

        public boolean trigger(Brain br) {
            Body b = br.body;
            boolean ore = b.has(Needs::rawOre);
            if (b.has(Moves::igniter) || hasLitLight(b)) return false;
            if (!ore && !needsFlame(br)) return false;
            boolean furnaceOk = b.has(s -> s.isOf(Items.FURNACE)) || (b.count(Items.COBBLESTONE) >= 8 && Moves.tableAt(br, 8));
            boolean bucketOk = b.has(Moves::emptyBucket) || (b.count(Moves::planks) >= 3 && Moves.tableAt(br, 8));
            if (!furnaceOk || !bucketOk) return false;
            lava = br.notebook.nearest(Notebook.Kind.LAVA, b.bot.getBlockPos(), 48);
            if (lava == null) return false;
            because = "lava at " + lava.pos.toShortString() + ": a bucket of it lights a furnace by itself";
            return true;
        }

        public Outcome tick(Brain br) {
            Body b = br.body;
            DummyPlayer bot = b.bot;
            switch (stage) {
                case 0 -> {   // a bucket
                    if (b.has(Moves::emptyBucket)) { stage = 1; craft = null; return RUNNING; }
                    if (craft == null) craft = new Orders.Craft(Registries.ITEM.get(new Identifier("unbeta-content", "wood_bucket")));
                    return Needs.craft(br, craft) == FAILED ? FAILED : RUNNING;
                }
                case 1 -> {   // a furnace
                    if (b.has(s -> s.isOf(Items.FURNACE))) { stage = 2; craft = null; return RUNNING; }
                    if (craft == null) craft = new Orders.Craft(Items.FURNACE);
                    return Needs.craft(br, craft) == FAILED ? FAILED : RUNNING;
                }
                case 2 -> {   // to the lava
                    if (bot.getEyePos().distanceTo(Vec3d.ofCenter(lava.pos)) <= 4) { stage = 3; return RUNNING; }
                    return b.walkTo(lava.pos, 3.0) == Body.Walk.FAILED ? FAILED : RUNNING;
                }
                case 3 -> {   // the furnace down beside it, ore in
                    Body.Placed p = b.placeNearby(Items.FURNACE, Blocks.FURNACE);
                    if (p.working()) return RUNNING;
                    if (p.failed()) return FAILED;
                    furnace = p.at();
                    br.notebook.note(Notebook.Kind.FURNACE, furnace, br.now(), "my furnace", furnace);
                    if (br.world().getBlockEntity(furnace) instanceof AbstractFurnaceBlockEntity oven) {
                        int slot = b.slot(Needs::rawOre);
                        if (slot >= 0) {
                            ItemStack ore = bot.getInventory().getStack(slot);
                            limit = ore.getCount() * 200 + 100;
                            oven.setStack(0, ore.copy());
                            bot.getInventory().setStack(slot, ItemStack.EMPTY);
                            oven.markDirty();
                        }
                    }
                    stage = 4;
                    return RUNNING;
                }
                case 4 -> {   // fill the bucket...
                    if (b.has(Moves::lavaBucket)) { stage = 5; return RUNNING; }
                    if (bot.getEyePos().distanceTo(Vec3d.ofCenter(lava.pos)) > 4.5) return b.walkTo(lava.pos, 3.0) == Body.Walk.FAILED ? FAILED : RUNNING;
                    b.stopMoving();
                    b.hold(Moves::emptyBucket);
                    if (b.lookAt(Vec3d.ofCenter(lava.pos).add(0, 0.4, 0)) && t++ % 8 == 0) b.useHeld();
                    return t > 80 ? FAILED : RUNNING;
                }
                case 5 -> {   // ...and straight into the fire box, before it burns through
                    if (!(br.world().getBlockEntity(furnace) instanceof AbstractFurnaceBlockEntity oven)) return FAILED;
                    if (!b.inReach(furnace)) return b.walkTo(furnace, 2.0) == Body.Walk.FAILED ? FAILED : RUNNING;
                    int slot = b.slot(Moves::lavaBucket);
                    if (slot < 0) return FAILED;
                    if (oven.getStack(1).isEmpty()) {
                        oven.setStack(1, bot.getInventory().getStack(slot).copy());
                        bot.getInventory().setStack(slot, ItemStack.EMPTY);
                        oven.markDirty();
                    }
                    b.lookAt(Vec3d.ofCenter(furnace));
                    bot.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                    stage = 6;
                    t = 0;
                    return RUNNING;
                }
                default -> {  // lit: a flame. Light a torch at it, cook, take the ingots.
                    BlockState st = br.world().getBlockState(furnace);
                    if (!(st.getBlock() instanceof AbstractFurnaceBlock)) return FAILED;
                    t++;
                    boolean lit = st.get(AbstractFurnaceBlock.LIT);
                    if (lit) br.notebook.note(Notebook.Kind.FLAME, furnace, br.now(), "lit Furnace", furnace);
                    if (!b.inReach(furnace)) return b.walkTo(furnace, 2.0) == Body.Walk.FAILED ? FAILED : RUNNING;
                    b.stopMoving();
                    b.lookAt(Vec3d.ofCenter(furnace));
                    if (lit && b.has(Know::unlitLight) && !hasLitLight(b) && t % 10 == 5) {
                        b.hold(Know::unlitLight);
                        b.useOn(furnace);
                        if (bot.currentScreenHandler != bot.playerScreenHandler) bot.closeHandledScreen();
                    }
                    if (!(br.world().getBlockEntity(furnace) instanceof AbstractFurnaceBlockEntity oven)) return FAILED;
                    boolean cooking = !oven.getStack(0).isEmpty() && t < limit;
                    if (cooking) return RUNNING;
                    ItemStack out = oven.getStack(2);
                    if (!out.isEmpty()) {
                        bot.getInventory().offerOrDrop(out.copy());
                        oven.setStack(2, ItemStack.EMPTY);
                        oven.markDirty();
                        bot.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                        br.chat(br.pickLine("ingots!", "smelted", "nice"));
                    }
                    return t > 30 ? DONE : RUNNING;
                }
            }
        }

        public void stopped(Brain br) {
            if (br.bot().currentScreenHandler != br.bot().playerScreenHandler) br.bot().closeHandledScreen();
        }
    }

    // ------------------------------------------------------------------ N24 torch juggling

    /**
     * Its lit torch is burning low: it sets it down, lights a fresh torch from it (lighting always
     * gives a full burn), snuffs the old one and picks it back up, unlit, for next time. With two
     * torches it never runs out of flame.
     */
    static final class Juggle extends Reaction {
        Juggle() { super("N24", "Torch juggling", NEED, 40, false, CRAFTING); }
        public Reaction fresh() { return new Juggle(); }

        private int stage;
        private BlockPos placed;
        private int t;

        /** The slot of a lit torch (36 = the off hand), or -1. */
        static int litTorchSlot(Body b) {
            if (TorchItems.isLitTorch(b.bot.getOffHandStack())) return 36;
            return b.slot(TorchItems::isLitTorch);
        }

        static ItemStack at(Body b, int slot) {
            return slot == 36 ? b.bot.getOffHandStack() : b.bot.getInventory().getStack(slot);
        }

        public boolean trigger(Brain br) {
            Body b = br.body;
            int slot = litTorchSlot(b);
            if (slot < 0) return false;
            ItemStack s = at(b, slot);
            long full = TorchItems.getFull(s);
            long left = TorchItems.getBurnoutAt(s) - br.now();
            if (full <= 0 || left <= 0 || left > full / 4) return false;
            if (b.bot.isTouchingWater()) return false;
            because = "my torch is burning low (" + left / 20 + "s left)";
            return true;
        }

        public Outcome tick(Brain br) {
            Body b = br.body;
            Block torch = Registries.BLOCK.get(new Identifier("unbeta-content", "torch"));
            switch (stage) {
                case 0 -> {   // set the dying torch down
                    int slot = litTorchSlot(b);
                    if (slot < 0) return FAILED;
                    if (slot == 36) b.toOffhand(b.bot.getInventory().selectedSlot);   // off hand -> main hand
                    Item lit = at(b, slot == 36 ? b.bot.getInventory().selectedSlot : slot).getItem();
                    placed = b.placeBeside(lit, torch);
                    if (placed == null) return ++t > 20 ? FAILED : RUNNING;
                    br.notebook.note(Notebook.Kind.FLAME, placed, br.now(), "my torch", placed);
                    stage = b.has(TorchItems::isUnlitTorch) ? 1 : 9;
                    t = 0;
                    return RUNNING;
                }
                case 1 -> {   // light a fresh one from it
                    if (b.has(TorchItems::isLitTorch)) { stage = 2; t = 0; return RUNNING; }
                    b.stopMoving();
                    b.hold(TorchItems::isUnlitTorch);
                    b.lookAt(Vec3d.ofCenter(placed));
                    if (t++ % 8 == 3) b.useOn(placed);
                    return t > 60 ? FAILED : RUNNING;
                }
                case 2 -> {   // snuff the old one (anything plain in hand puts it out)
                    if (!Senses.isFlame(br.world().getBlockState(placed))) {
                        br.notebook.forget(Notebook.Kind.FLAME, placed);
                        stage = 3;
                        return RUNNING;
                    }
                    b.holdPlain();
                    b.lookAt(Vec3d.ofCenter(placed));
                    if (t++ % 8 == 3) b.useOn(placed);
                    return t > 60 ? DONE : RUNNING;    // couldn't snuff it: it stays as a flame
                }
                case 3 -> {   // and pick it up, unlit, for next time
                    Outcome o = Needs.mine(br, placed);
                    return o == RUNNING ? RUNNING : DONE;
                }
                default -> {  // no spare torch: the dying one stays down as a flame to light from
                    return DONE;
                }
            }
        }
    }

    // ------------------------------------------------------------------ N26 flint from gravel

    /**
     * Wants flint (for flint and steel, or arrows): mines gravel, and if no flint drops, sets the
     * gravel back down and mines it again, and again, until one does.
     */
    static final class Flint extends Reaction {
        Flint() { super("N26", "Flint from gravel", NEED, 120, false, GATHERING); }
        public Reaction fresh() { return new Flint(); }

        private Notebook.Mark gravel;
        private BlockPos placed;
        private int cycles;
        private Orders.Craft craft;

        static boolean wantsFlint(Brain br) {
            Body b = br.body;
            if (b.count(Items.FLINT) > 0) return false;
            boolean steel = b.count(Items.IRON_INGOT) >= 1 && !b.has(s -> s.isOf(Items.FLINT_AND_STEEL));
            Traits.Keep k = br.traits().keep();
            boolean arrows = (k == Traits.Keep.FLETCHER || k == Traits.Keep.DUELIST) && b.count(Items.FEATHER) > 0
                    && b.count(s -> Moves.id(s).getPath().endsWith("_nugget")) == 0;
            return steel || arrows;
        }

        public boolean trigger(Brain br) {
            if (!wantsFlint(br)) return false;
            if (!br.body.has(s -> s.isOf(Items.GRAVEL)) && br.notebook.nearest(Notebook.Kind.GRAVEL, br.bot().getBlockPos(), 32) == null) return false;
            because = "I want flint: gravel gives it, if I keep at it";
            return true;
        }

        public Outcome tick(Brain br) {
            Body b = br.body;
            if (b.count(Items.FLINT) > 0) {
                if (b.count(Items.IRON_INGOT) == 0 || b.has(s -> s.isOf(Items.FLINT_AND_STEEL))) return DONE;
                if (craft == null) craft = new Orders.Craft(Items.FLINT_AND_STEEL);
                return Needs.craft(br, craft);
            }
            if (cycles > 30) return FAILED;
            if (placed != null) {
                Outcome o = Needs.mine(br, placed);
                if (o == RUNNING) return RUNNING;
                placed = null;
                cycles++;
                return RUNNING;
            }
            if (b.has(s -> s.isOf(Items.GRAVEL))) {
                placed = b.placeBeside(Items.GRAVEL, Blocks.GRAVEL);
                return placed == null ? FAILED : RUNNING;
            }
            if (gravel == null || !Moves.stillThere(br, gravel)) {
                gravel = br.notebook.nearest(Notebook.Kind.GRAVEL, b.bot.getBlockPos(), 32);
                if (gravel == null) return FAILED;
            }
            Outcome o = Needs.mine(br, gravel.pos);
            if (o != RUNNING) {
                br.notebook.forget(gravel.kind, gravel.pos);
                gravel = null;
                cycles++;
                if (o == FAILED) return FAILED;
            }
            return RUNNING;
        }
    }

    // ------------------------------------------------------------------ N27 shears and a Jack o'Lantern

    /**
     * Once it has an iron pick and iron to spare: shears, then a pumpkin sheared into a Jack
     * o'Lantern (waterproof, and it burns 20 minutes), mined up to carry.
     */
    static final class Lantern extends Reaction {
        Lantern() { super("N27", "Jack o'Lantern", NEED, 60, false, CRAFTING); }
        public Reaction fresh() { return new Lantern(); }

        private Notebook.Mark pumpkin;
        private Orders.Craft craft;
        private boolean sheared;
        private int t;

        public boolean trigger(Brain br) {
            Body b = br.body;
            if (b.has(Know::jackOLantern)) return false;
            boolean shears = b.has(s -> s.isOf(Items.SHEARS));
            if (!shears && !(b.count(Items.IRON_INGOT) >= 2 && ironPick(b))) return false;
            pumpkin = br.notebook.nearest(Notebook.Kind.PUMPKIN, b.bot.getBlockPos(), 48);
            if (pumpkin == null) return false;
            because = "a pumpkin: shears make it a Jack o'Lantern, the waterproof light";
            return true;
        }

        public Outcome tick(Brain br) {
            Body b = br.body;
            if (b.has(Know::jackOLantern)) return DONE;
            if (!b.has(s -> s.isOf(Items.SHEARS))) {
                if (craft == null) craft = new Orders.Craft(Items.SHEARS);
                return Needs.craft(br, craft) == FAILED ? FAILED : RUNNING;
            }
            BlockState st = br.world().getBlockState(pumpkin.pos);
            boolean isPumpkin = st.isOf(Blocks.PUMPKIN);
            if (!sheared && !isPumpkin) return FAILED;
            if (sheared || !isPumpkin) return Needs.mine(br, pumpkin.pos);   // now a Jack o'Lantern block: take it
            if (!b.inReach(pumpkin.pos)) return b.walkTo(pumpkin.pos, 2.5) == Body.Walk.FAILED ? FAILED : RUNNING;
            b.stopMoving();
            b.hold(s -> s.isOf(Items.SHEARS));
            b.lookAt(Vec3d.ofCenter(pumpkin.pos));
            if (t++ % 10 == 5) {
                b.useOn(pumpkin.pos);
                if (!br.world().getBlockState(pumpkin.pos).isOf(Blocks.PUMPKIN)) sheared = true;
            }
            return t > 60 ? FAILED : RUNNING;
        }
    }

    // ------------------------------------------------------------------ N31 charcoal

    /** No coal for torches: it cooks logs into charcoal at a furnace it can light. */
    static final class Charcoal extends Reaction {
        Charcoal() { super("N31", "Charcoal", NEED, 120, false, CRAFTING); }
        public Reaction fresh() { return new Charcoal(); }

        private final Orders.Smelt smelt = new Orders.Smelt(Moves::log);

        public boolean trigger(Brain br) {
            Body b = br.body;
            if (b.count(Moves::log) < 3 || b.count(s -> s.isOf(Items.COAL) || s.isOf(Items.CHARCOAL)) > 0) return false;
            if (b.count(s -> TorchItems.isTorchItem(s)) >= 4) return false;
            boolean flame = b.has(Moves::igniter) || hasLitLight(b);
            boolean furnace = b.has(s -> s.isOf(Items.FURNACE)) || br.notebook.nearest(Notebook.Kind.FURNACE, b.bot.getBlockPos(), 8) != null
                    || br.notebook.nearest(Notebook.Kind.FLAME, b.bot.getBlockPos(), 8, m -> m.what.contains("Furnace")) != null;
            if (!flame || !furnace) return false;
            because = "no coal: logs cook into charcoal for torches";
            return true;
        }

        public Outcome tick(Brain br) {
            if (!smelt.tick(br.body)) return RUNNING;
            return br.body.lastSaid.startsWith("done") ? DONE : FAILED;
        }
    }

    // ------------------------------------------------------------------ N28 boats

    /** Knows there's water about and has wood and a table: makes a boat for later. */
    static final class MakeBoat extends Reaction {
        MakeBoat() { super("N28b", "Make a boat", NEED, 20, false, CRAFTING); }
        public Reaction fresh() { return new MakeBoat(); }

        private final Orders.Craft craft = new Orders.Craft(s -> s.getItem() instanceof BoatItem, "a boat");

        public boolean trigger(Brain br) {
            Body b = br.body;
            if (b.has(s -> s.getItem() instanceof BoatItem) || !b.has(Moves::pick)) return false;
            if (b.count(Moves::planks) + b.count(Moves::log) * 4 < 9 || !Moves.tableAt(br, 8)) return false;
            if (br.notebook.all(Notebook.Kind.WATER).size() < 4 && br.swimTicks == 0) return false;
            because = "plenty of water about: a boat beats swimming";
            return true;
        }

        public Outcome tick(Brain br) {
            return Needs.craft(br, craft);
        }
    }

    /** A long swim: drops its boat on the water, rows it where it was going, and takes it back ashore. */
    static final class Boat extends Reaction {
        Boat() { super("N28", "Boat", NEED, 180, false); }
        public Reaction fresh() { return new Boat(); }

        private Vec3d target;
        private BoatEntity boat;
        private int stage;
        private int t;
        private Vec3d lastPos;
        private int still;

        public boolean trigger(Brain br) {
            Body b = br.body;
            if (br.swimTicks < 40 || b.bot.hasVehicle() || !b.has(s -> s.getItem() instanceof BoatItem)) return false;
            if (b.bot.isSubmergedInWater() && b.bot.getAir() < b.bot.getMaxAir() / 3) return false;
            BlockPos g = b.goal();
            target = g != null ? Vec3d.ofCenter(g)
                    : b.bot.getPos().add(b.bot.getRotationVector().multiply(1, 0, 1).normalize().multiply(48));
            because = "a long swim: into my boat";
            return true;
        }

        public Outcome tick(Brain br) {
            Body b = br.body;
            DummyPlayer bot = b.bot;
            switch (stage) {
                case 0 -> {   // put the boat on the water and get in
                    int slot = b.slot(s -> s.getItem() instanceof BoatItem);
                    if (slot < 0) return FAILED;
                    BoatEntity be = new BoatEntity(br.world(), bot.getX(), bot.getY() + 0.2, bot.getZ());
                    be.setYaw(bot.getYaw());
                    br.world().spawnEntity(be);
                    bot.getInventory().getStack(slot).decrement(1);
                    bot.startRiding(be);
                    boat = be;
                    stage = 1;
                    lastPos = be.getPos();
                    return RUNNING;
                }
                case 1 -> {   // row
                    if (!bot.hasVehicle() || boat.isRemoved()) { stage = 3; return RUNNING; }
                    Vec3d to = target.subtract(boat.getPos()).multiply(1, 0, 1);
                    if (to.length() < 3) { stage = 2; return RUNNING; }
                    Vec3d dir = to.normalize();
                    float yaw = (float) (MathHelper.atan2(dir.z, dir.x) * 180 / Math.PI) - 90F;
                    boat.setYaw(yaw);
                    bot.setYaw(yaw);
                    bot.setHeadYaw(yaw);
                    BlockPos ahead = BlockPos.ofFloored(boat.getPos().add(dir.multiply(1.3)));
                    boolean water = br.world().getFluidState(ahead).isIn(FluidTags.WATER) || br.world().getFluidState(ahead.down()).isIn(FluidTags.WATER);
                    if (!water && !br.world().getBlockState(ahead).isAir()) { stage = 2; return RUNNING; }   // shore ahead
                    if (!water && br.world().getBlockState(ahead.down()).isSolid()) { stage = 2; return RUNNING; }
                    boat.move(MovementType.SELF, dir.multiply(BotConfig.get().boatSpeed));
                    if (++t % 40 == 0) {
                        if (boat.getPos().distanceTo(lastPos) < 1) { stage = 2; return RUNNING; }   // stuck
                        lastPos = boat.getPos();
                    }
                    return RUNNING;
                }
                case 2 -> {   // ashore
                    bot.stopRiding();
                    stage = 3;
                    t = 0;
                    return RUNNING;
                }
                default -> {  // take the boat back
                    if (boat == null || boat.isRemoved()) return DONE;
                    if (++t > 100) {
                        boat.discard();
                        bot.getInventory().offerOrDrop(new ItemStack(Items.OAK_BOAT));
                        return DONE;
                    }
                    b.lookAt(boat.getPos());
                    if (bot.distanceTo(boat) > 2.5) b.walkTo(boat.getBlockPos(), 1.5);
                    else if (t % 4 == 0) {
                        bot.attack(boat);
                        bot.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                    }
                    return RUNNING;
                }
            }
        }

        public void stopped(Brain br) {
            if (br.bot().hasVehicle()) br.bot().stopRiding();
        }
    }

    // ------------------------------------------------------------------ N29 delving: caves and rails

    /**
     * No ore in sight: it goes down through the caves it has seen into, and along old rails (they
     * run past ore, deeper is better), stopping to look all around at each.
     */
    static final class Delve extends Reaction {
        Delve() { super("N29", "Delve", NEED, 90, false, GATHERING); }
        public Reaction fresh() { return new Delve(); }

        private Notebook.Mark way;
        private int scan;

        static boolean wantsOre(Brain br) {
            Body b = br.body;
            if (!b.has(Moves::pick)) return false;
            if (ironPick(b)) return b.count(Items.IRON_INGOT) + b.count(Items.RAW_IRON) < 6 || b.count(Items.DIAMOND) < 3;
            return true;
        }

        static boolean usefulOreNear(Brain br, int r) {
            return br.notebook.nearest(Notebook.Kind.ORE, br.bot().getBlockPos(), r, m -> Needs.Ore.worth(m) <= 2) != null;
        }

        public boolean trigger(Brain br) {
            if (!wantsOre(br) || usefulOreNear(br, 32)) return false;
            BlockPos me = br.bot().getBlockPos();
            Notebook.Mark best = null;
            double bestScore = Double.MAX_VALUE;
            for (Notebook.Kind k : new Notebook.Kind[]{Notebook.Kind.RAIL, Notebook.Kind.CAVE}) {
                for (Notebook.Mark m : br.notebook.all(k)) {
                    double d = Math.sqrt(m.pos.getSquaredDistance(me));
                    if (d > 64 || d < 3) continue;
                    double score = m.pos.getY() * 1.5 + d * 0.5 - (k == Notebook.Kind.RAIL ? 24 : 0);
                    if (score < bestScore) {
                        bestScore = score;
                        best = m;
                    }
                }
            }
            if (best == null) return false;
            way = best;
            because = "no ore in sight: down to the " + best.kind.label + " at " + best.pos.toShortString();
            return true;
        }

        public Outcome tick(Brain br) {
            Body b = br.body;
            if (scan > 0) {
                b.stopMoving();
                float yaw = b.bot.getYaw() + 9F;
                b.bot.setYaw(yaw);
                b.bot.setHeadYaw(yaw);
                b.bot.setPitch(MathHelper.sin(scan / 6F) * 40F);
                return --scan == 0 ? DONE : RUNNING;
            }
            Body.Walk w = b.walkTo(way.pos, 2.0);
            if (w == Body.Walk.WALKING) return RUNNING;
            br.notebook.forget(way.kind, way.pos);
            if (w == Body.Walk.FAILED) return FAILED;
            scan = 40;
            return RUNNING;
        }
    }

    // ------------------------------------------------------------------ N30 staircase and strip mine

    /**
     * With an iron pick, stone is no barrier: it digs a staircase down to ore depth, then a long
     * tunnel, its eyes on the walls. It never digs toward lava or water, and turns away instead.
     */
    static final class Excavate extends Reaction {
        Excavate() { super("N30", "Dig down for ore", NEED, 240, false, GATHERING); }
        public Reaction fresh() { return new Excavate(); }

        private final Moves.Staircase stairs = new Moves.Staircase();

        public boolean trigger(Brain br) {
            Body b = br.body;
            if (!ironPick(b) || br.excavated > BotConfig.get().excavateTicks) return false;
            if (!Delve.wantsOre(br) || Delve.usefulOreNear(br, 24)) return false;
            if (b.bot.isTouchingWater()) return false;
            because = "iron pick: a staircase down to the ore";
            return true;
        }

        public Outcome tick(Brain br) {
            br.excavated++;
            return stairs.tick(br, BotConfig.get().mineDepthY, 32);
        }
    }
}
