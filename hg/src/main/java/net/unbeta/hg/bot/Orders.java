package net.unbeta.hg.bot;

import net.minecraft.block.Blocks;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.UUID;

/**
 * Phase 1's orders, given with /hg bot: come, go to, mine, craft, smelt. From Phase 2 the same
 * moves are what a bot's own Reactions are made of.
 */
public final class Orders {

    private Orders() {}

    // ------------------------------------------------------------------ come / go to

    /** Walk to a player, wherever they go, until close. */
    public static final class Come implements Body.Action {
        private final UUID who;
        private final String name;

        public Come(ServerPlayerEntity player) {
            this.who = player.getUuid();
            this.name = player.getName().getString();
        }

        @Override
        public boolean tick(Body body) {
            ServerPlayerEntity p = body.bot.getServer().getPlayerManager().getPlayer(who);
            if (p == null || p.getWorld() != body.bot.getWorld()) {
                body.say("lost track of " + name);
                return true;
            }
            switch (body.walkTo(p.getBlockPos(), 2.5)) {
                case ARRIVED -> {
                    body.lookAt(p.getEyePos());
                    body.say("here, " + name);
                    return true;
                }
                case FAILED -> {
                    body.say("can't find a way to you, " + name);
                    return true;
                }
                default -> {
                    return false;
                }
            }
        }

        @Override
        public String describe() {
            return "coming to " + name;
        }
    }

    /** Walk to a spot. */
    public static final class GoTo implements Body.Action {
        private final BlockPos to;

        public GoTo(BlockPos to) {
            this.to = to.toImmutable();
        }

        @Override
        public boolean tick(Body body) {
            switch (body.walkTo(to, 1.5)) {
                case ARRIVED -> {
                    body.say("made it to " + to.toShortString());
                    return true;
                }
                case FAILED -> {
                    body.say("can't find a way to " + to.toShortString());
                    return true;
                }
                default -> {
                    return false;
                }
            }
        }

        @Override
        public String describe() {
            return "walking to " + to.toShortString();
        }
    }

    // ------------------------------------------------------------------ mine

    /** Walk up to a block and break it. */
    public static final class Mine implements Body.Action {
        private final BlockPos at;

        public Mine(BlockPos at) {
            this.at = at.toImmutable();
        }

        @Override
        public boolean tick(Body body) {
            if (!body.inReach(at)) {
                if (body.walkTo(at, 2.0) == Body.Walk.FAILED) {
                    body.say("can't get to the block at " + at.toShortString());
                    return true;
                }
                if (!body.inReach(at)) return false;
            }
            return switch (body.dig(at)) {
                case DONE -> {
                    body.say("mined the block at " + at.toShortString());
                    yield true;
                }
                case FAILED -> {
                    body.say("couldn't break the block at " + at.toShortString());
                    yield true;
                }
                default -> false;
            };
        }

        @Override
        public String describe() {
            return "mining at " + at.toShortString();
        }
    }

    // ------------------------------------------------------------------ craft

    /** Craft an item, making whatever it needs on the way (planks, sticks, a crafting table). */
    public static final class Craft implements Body.Action {
        private final Item target;
        private List<Crafting.Step> steps;
        private int step;
        private int timer;
        private BlockPos table;
        private boolean planned;
        /** Set once the crafting has finished well (for Smelt, which crafts a furnace this way). */
        public boolean succeeded;

        public Craft(Item target) {
            this.target = target;
        }

        @Override
        public boolean tick(Body body) {
            if (!planned) {
                planned = true;
                table = body.findNear(Blocks.CRAFTING_TABLE, 8);
                steps = Crafting.plan(body.bot, target, table != null);
                if (steps == null) {
                    body.say("can't make " + name(target) + " from what I'm carrying");
                    return true;
                }
            }
            if (step >= steps.size()) {
                body.hold(target);
                body.say("made " + name(target));
                succeeded = true;
                return true;
            }
            Crafting.Step s = steps.get(step);
            if (s.placeTable()) {
                Body.Placed placed = body.placeNearby(Items.CRAFTING_TABLE, Blocks.CRAFTING_TABLE);
                if (placed.working()) return false;
                if (placed.failed()) {
                    body.say("nowhere to put my crafting table");
                    return true;
                }
                table = placed.at();
                step++;
                return false;
            }
            if (s.needsTable()) {
                if (table == null || !body.world().getBlockState(table).isOf(Blocks.CRAFTING_TABLE)) {
                    table = body.findNear(Blocks.CRAFTING_TABLE, 8);
                    if (table == null && Crafting.has(body.bot, Items.CRAFTING_TABLE)) {
                        // Carrying one: put it down.
                        Body.Placed placed = body.placeNearby(Items.CRAFTING_TABLE, Blocks.CRAFTING_TABLE);
                        if (placed.working()) return false;
                        if (placed.failed()) {
                            body.say("nowhere to put my crafting table");
                            return true;
                        }
                        table = placed.at();
                        return false;
                    }
                    if (table == null) {
                        body.say("I need a crafting table and can't make one");
                        return true;
                    }
                }
                if (!body.inReach(table)) {
                    if (body.walkTo(table, 2.0) == Body.Walk.FAILED) {
                        body.say("can't get to the crafting table");
                        return true;
                    }
                    return false;
                }
                body.lookAt(Vec3d.ofCenter(table));
            } else {
                // 2x2 crafting: looks down at its hands.
                body.lookAt(body.bot.getEyePos().add(body.bot.getRotationVector().multiply(1, 0, 1)).add(0, -1.2, 0));
            }
            if (timer % 5 == 0) body.bot.swingHand(Hand.MAIN_HAND);
            if (++timer < BotConfig.get().craftTicks) return false;
            timer = 0;
            if (!Crafting.canDo(body.bot, s)) {
                body.say("ran short of ingredients for " + name(s.output().getItem()));
                return true;
            }
            Crafting.craft(body.bot, s);
            step++;
            return false;
        }

        @Override
        public String describe() {
            return "crafting " + name(target);
        }
    }

    // ------------------------------------------------------------------ smelt

    /** Smelt everything smeltable it carries in a real furnace (making and placing one if needed), then take it all out. */
    public static final class Smelt implements Body.Action {
        private BlockPos furnace;
        private Craft makingFurnace;
        private boolean loaded;
        private int lightTries;
        private int waited;
        private int limit;

        @Override
        public boolean tick(Body body) {
            if (furnace == null) {
                furnace = body.findNear(Blocks.FURNACE, 8);
                if (furnace == null) {
                    if (!Crafting.has(body.bot, Items.FURNACE)) {
                        if (makingFurnace == null) makingFurnace = new Craft(Items.FURNACE);
                        if (!makingFurnace.tick(body)) return false;
                        if (!makingFurnace.succeeded) return true;
                    }
                    Body.Placed placed = body.placeNearby(Items.FURNACE, Blocks.FURNACE);
                    if (placed.working()) return false;
                    if (placed.failed()) {
                        body.say("nowhere to put my furnace");
                        return true;
                    }
                    furnace = placed.at();
                }
            }
            if (!body.inReach(furnace)) {
                if (body.walkTo(furnace, 2.0) == Body.Walk.FAILED) {
                    body.say("can't get to the furnace");
                    return true;
                }
                return false;
            }
            BlockEntity be = body.world().getBlockEntity(furnace);
            if (!(be instanceof AbstractFurnaceBlockEntity oven)) {
                body.say("the furnace is gone");
                return true;
            }
            body.lookAt(Vec3d.ofCenter(furnace));
            if (!loaded) return load(body, oven);
            // Unbeta furnaces don't light themselves: strike a light on it, as a player would.
            boolean lit = body.world().getBlockState(furnace).get(net.minecraft.block.AbstractFurnaceBlock.LIT);
            if (!lit && waited < 40) {
                if (waited % 10 == 0) {
                    if (!light(body)) {
                        body.say("I need flint and steel, a lit torch or lava to light the furnace");
                        takeBack(body, oven);
                        return true;
                    }
                }
                waited++;
                return false;
            }
            if (!lit && oven.getStack(0).getCount() > 0 && waited == 40) {
                body.say("the furnace won't light");
                takeBack(body, oven);
                return true;
            }
            // Standing guard while it cooks.
            waited++;
            if (waited % 40 == 0) body.bot.swingHand(Hand.MAIN_HAND);
            boolean done = oven.getStack(0).isEmpty() && waited > 20;
            if (!done && waited < limit) return false;
            takeBack(body, oven);
            body.say(done ? "done smelting" : "gave up waiting on the furnace");
            return true;
        }

        /** Takes everything out of the furnace. */
        private void takeBack(Body body, AbstractFurnaceBlockEntity oven) {
            PlayerInventory inv = body.bot.getInventory();
            for (int slot = 0; slot < 3; slot++) {
                ItemStack s = oven.getStack(slot);
                if (!s.isEmpty()) inv.offerOrDrop(s.copy());
                oven.setStack(slot, ItemStack.EMPTY);
            }
            oven.markDirty();
            body.bot.swingHand(Hand.MAIN_HAND);
        }

        /**
         * Uses an igniter on the furnace (flint and steel, a lit torch, a lit Jack o'Lantern).
         * Lava fuel needs none: true without striking. False if it has no way to light it.
         */
        private boolean light(Body body) {
            BlockEntity be = body.world().getBlockEntity(furnace);
            if (be instanceof AbstractFurnaceBlockEntity oven
                    && net.unbeta.content.furnace.LavaFuel.burnTime(oven.getStack(1)) > 0) return true;
            PlayerInventory inv = body.bot.getInventory();
            int igniter = -1;
            for (int i = 0; i < 36 && igniter < 0; i++) {
                ItemStack s = inv.getStack(i);
                if (s.isOf(Items.FLINT_AND_STEEL) || net.unbeta.content.torch.TorchItems.isLitTorch(s)
                        || net.unbeta.content.jackolantern.JackOLanternItems.isLit(s)) igniter = i;
            }
            if (igniter < 0) return false;
            body.holdSlot(igniter);
            net.minecraft.util.math.Direction face = net.minecraft.util.math.Direction.getFacing(
                    body.bot.getX() - (furnace.getX() + 0.5), 0, body.bot.getZ() - (furnace.getZ() + 0.5));
            net.minecraft.util.hit.BlockHitResult hit = new net.minecraft.util.hit.BlockHitResult(
                    Vec3d.ofCenter(furnace).add(Vec3d.of(face.getVector()).multiply(0.5)), face, furnace, false);
            body.bot.interactionManager.interactBlock(body.bot, body.world(), body.bot.getMainHandStack(), Hand.MAIN_HAND, hit);
            body.bot.swingHand(Hand.MAIN_HAND);
            return true;
        }

        private boolean load(Body body, AbstractFurnaceBlockEntity oven) {
            PlayerInventory inv = body.bot.getInventory();
            int ore = -1, fuel = -1, oreRank = Integer.MAX_VALUE;
            for (int i = 0; i < 36; i++) {
                ItemStack s = inv.getStack(i);
                if (s.isEmpty()) continue;
                boolean isFuel = AbstractFurnaceBlockEntity.canUseAsFuel(s);
                boolean smeltable = !isFuel && body.bot.getServer().getRecipeManager()
                        .getFirstMatch(RecipeType.SMELTING, new SimpleInventory(s), body.world()).isPresent();
                if (smeltable && rank(s) < oreRank) {
                    ore = i;
                    oreRank = rank(s);
                } else if (fuel < 0 && isFuel) {
                    fuel = i;
                }
            }
            if (ore < 0) {
                body.say("I have nothing to smelt");
                return true;
            }
            if (fuel < 0) {
                body.say("I have no fuel");
                return true;
            }
            if (!oven.getStack(0).isEmpty() || !oven.getStack(1).isEmpty() || !oven.getStack(2).isEmpty()) {
                body.say("that furnace is in use");
                return true;
            }
            ItemStack in = inv.getStack(ore);
            limit = in.getCount() * 200 + 200;
            oven.setStack(0, in.copy());
            inv.setStack(ore, ItemStack.EMPTY);
            oven.setStack(1, inv.getStack(fuel).copy());
            inv.setStack(fuel, ItemStack.EMPTY);
            oven.markDirty();
            body.bot.swingHand(Hand.MAIN_HAND);
            loaded = true;
            return false;
        }

        /** What's most worth smelting: ore first, then raw food, then anything else (cobblestone, sand) last. */
        private static int rank(ItemStack s) {
            String path = net.minecraft.registry.Registries.ITEM.getId(s.getItem()).getPath();
            if (path.startsWith("raw_") || path.endsWith("_ore")) return 0;
            if (s.isFood()) return 1;
            return 2;
        }

        @Override
        public String describe() {
            return "smelting";
        }
    }

    static String name(Item item) {
        return item.getName().getString();
    }
}
