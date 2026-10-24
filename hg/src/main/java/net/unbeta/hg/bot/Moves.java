package net.unbeta.hg.bot;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PickaxeItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.unbeta.content.jackolantern.JackOLanternItems;
import net.unbeta.content.torch.TorchItems;

/**
 * What items are to a bot, and the small routines several Reactions share: eating, foraging,
 * hiding in a sealed hole for the night, lighting a torch at a flame.
 */
final class Moves {

    private Moves() {}

    // ------------------------------------------------------------------ items

    static Identifier id(ItemStack s) {
        return Registries.ITEM.getId(s.getItem());
    }

    static boolean waterBucket(ItemStack s) {
        return id(s).getPath().endsWith("water_bucket");
    }

    /** A lava bucket that holds lava (an ice one freezes it to cobblestone). */
    static boolean lavaBucket(ItemStack s) {
        String p = id(s).getPath();
        return p.endsWith("lava_bucket") && !p.startsWith("ice_");
    }

    static boolean emptyBucket(ItemStack s) {
        String p = id(s).getPath();
        return p.equals("bucket") || p.equals("wood_bucket") || p.equals("copper_bucket");
    }

    /** Food worth eating: nothing that infects, poisons or freezes you. Golden apples are saved for the cure. */
    static boolean food(ItemStack s) {
        if (!s.isFood()) return false;
        if (s.isOf(Items.ROTTEN_FLESH) || s.isOf(Items.SPIDER_EYE) || s.isOf(Items.POISONOUS_POTATO)
                || s.isOf(Items.PUFFERFISH) || s.isOf(Items.GOLDEN_APPLE) || s.isOf(Items.ENCHANTED_GOLDEN_APPLE)) return false;
        String p = id(s).getPath();
        return !p.contains("scream") && !p.contains("golden_feather");
    }

    static int foodValue(ItemStack s) {
        return s.getItem().getFoodComponent() == null ? 0 : s.getItem().getFoodComponent().getHunger();
    }

    static boolean pick(ItemStack s) {
        return s.getItem() instanceof PickaxeItem;
    }

    static boolean weapon(ItemStack s) {
        return Body.weaponDamage(s) > 0;
    }

    static boolean log(ItemStack s) {
        return s.isIn(ItemTags.LOGS);
    }

    static boolean planks(ItemStack s) {
        return s.isIn(ItemTags.PLANKS);
    }

    static boolean unlitTorch(ItemStack s) {
        return TorchItems.isUnlitTorch(s);
    }

    static boolean litTorch(ItemStack s) {
        return TorchItems.isLitTorch(s);
    }

    /** Something to light a furnace with: flint and steel, a lit torch or Jack o'Lantern, or lava as fuel. */
    static boolean igniter(ItemStack s) {
        return s.isOf(Items.FLINT_AND_STEEL) || litTorch(s) || JackOLanternItems.isLit(s) || lavaBucket(s);
    }

    static boolean boomSpore(ItemStack s) {
        Identifier i = id(s);
        return i.getNamespace().equals("unbeta-content") && i.getPath().equals("boom_spore");
    }

    /** A plain full block it can wall itself in with. */
    static boolean buildingBlock(ItemStack s) {
        if (!(s.getItem() instanceof BlockItem bi)) return false;
        BlockState st = bi.getBlock().getDefaultState();
        return st.isOpaque() && st.getBlock().getHardness() >= 0
                && !s.isOf(Items.CRAFTING_TABLE) && !s.isOf(Items.FURNACE) && !s.isOf(Items.CHEST)
                && !s.isOf(Items.TNT) && !s.isOf(Items.GRAVEL) && !s.isOf(Items.SAND);
    }

    /** Wood in planks: a log counts as 4. */
    static int wood(Body b) {
        return b.count(Moves::log) * 4 + b.count(Moves::planks);
    }

    /** Carrying a crafting table, or knows one within range. */
    static boolean tableAt(Brain br, double range) {
        return br.body.has(s -> s.isOf(Items.CRAFTING_TABLE))
                || br.notebook.nearest(Notebook.Kind.TABLE, br.bot().getBlockPos(), range) != null;
    }

    static boolean furnaceAt(Brain br, double range) {
        return br.body.has(s -> s.isOf(Items.FURNACE))
                || br.notebook.nearest(Notebook.Kind.FURNACE, br.bot().getBlockPos(), range) != null
                || br.notebook.nearest(Notebook.Kind.FLAME, br.bot().getBlockPos(), range, m -> m.what.contains("Furnace")) != null;
    }

    static boolean emptySlot(Body b) {
        for (int i = 0; i < 36; i++) if (b.bot.getInventory().getStack(i).isEmpty()) return true;
        return false;
    }

    /** Animals worth hunting for food (never the zombie kinds). */
    static boolean prey(Entity e) {
        if (!(e instanceof LivingEntity le) || le.isDead() || le.isBaby()) return false;
        EntityType<?> t = e.getType();
        return t == EntityType.COW || t == EntityType.PIG || t == EntityType.SHEEP || t == EntityType.CHICKEN || t == EntityType.RABBIT;
    }

    /** Is the mark still what it was, if it's close enough to tell? (Crosses it out if not.) */
    static boolean stillThere(Brain br, Notebook.Mark m) {
        if (m.pos.getSquaredDistance(br.bot().getBlockPos()) > 36) return true;
        BlockState st = br.world().getBlockState(m.pos);
        if (Senses.classify(br.world(), m.pos, st) == m.kind) return true;
        br.notebook.forget(m.kind, m.pos);
        return false;
    }

    // ------------------------------------------------------------------ eating

    static final class Eating {
        private boolean started;
        private int waited;

        Reaction.Outcome tick(Body b, java.util.function.Predicate<ItemStack> what) {
            b.stopMoving();
            if (!started) {
                if (!b.hold(what)) return Reaction.Outcome.FAILED;
                b.lookAt(b.bot.getEyePos().add(b.bot.getRotationVector().multiply(1, 0, 1)).add(0, -0.6, 0));
                if (!b.useHeld()) return Reaction.Outcome.FAILED;
                started = true;
                return Reaction.Outcome.RUNNING;
            }
            if (b.bot.isUsingItem() && ++waited < 100) return Reaction.Outcome.RUNNING;
            return Reaction.Outcome.DONE;
        }
    }

    /** Eats its best food (most hunger back). */
    static int bestFoodSlot(Body b) {
        int best = -1, bestV = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = b.bot.getInventory().getStack(i);
            if (food(s) && foodValue(s) > bestV) {
                bestV = foodValue(s);
                best = i;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ foraging

    static final class Forage {
        private LivingEntity prey;

        Reaction.Outcome tick(Brain br) {
            Body b = br.body;
            // Food lying about: pick it up (walking over it does that).
            ItemEntity lying = null;
            double best = 100;
            for (ItemEntity it : br.senses.visible(ItemEntity.class)) {
                double d = it.squaredDistanceTo(b.bot);
                if (food(it.getStack()) && d < best) {
                    best = d;
                    lying = it;
                }
            }
            if (lying != null) {
                b.walkTo(lying.getBlockPos(), 0.4);
                return Reaction.Outcome.RUNNING;
            }
            if (prey == null || prey.isRemoved() || prey.isDead() || !br.senses.sees(prey)) {
                prey = null;
                double bestP = 24 * 24;
                for (Entity e : br.senses.visible()) {
                    if (prey(e) && e.squaredDistanceTo(b.bot) < bestP) {
                        bestP = e.squaredDistanceTo(b.bot);
                        prey = (LivingEntity) e;
                    }
                }
            }
            if (prey != null) {
                b.holdBestWeapon();
                b.attack(prey);
                return Reaction.Outcome.RUNNING;
            }
            Notebook.Mark leaves = br.notebook.nearest(Notebook.Kind.LEAVES, b.bot.getBlockPos(), 24);
            if (leaves != null && stillThere(br, leaves)) {
                if (!b.inReach(leaves.pos)) {
                    if (b.walkTo(leaves.pos, 3.0) == Body.Walk.FAILED) br.notebook.forget(leaves.kind, leaves.pos);
                    return Reaction.Outcome.RUNNING;
                }
                if (b.dig(leaves.pos) != Body.Dig.DIGGING) br.notebook.forget(leaves.kind, leaves.pos);
                return Reaction.Outcome.RUNNING;
            }
            return Reaction.Outcome.FAILED;
        }
    }

    // ------------------------------------------------------------------ hiding for the night

    /** Digs 3 down, seals the hole over its head, waits; climbs out when told to. */
    static final class Hide {
        private BlockPos top;
        private int dug;
        private boolean sealed;
        private boolean leaving;
        private int climbed;
        private double climbFrom;
        private boolean begun;
        private int centring;
        private int falling;

        /** Did it get as far as starting to dig? */
        boolean started() {
            return begun;
        }

        Reaction.Outcome tick(Brain br, boolean stillNeeded) {
            Body b = br.body;
            if (top == null) {
                // Stand square on one block first, or it digs a hole beside its feet and never drops in.
                BlockPos here = b.bot.getBlockPos();
                Vec3d centre = Vec3d.ofBottomCenter(here);
                if (b.bot.getPos().multiply(1, 0, 1).distanceTo(centre.multiply(1, 0, 1)) > 0.2 && centring++ < 30) {
                    b.lookAt(centre.add(0, b.bot.getStandingEyeHeight(), 0));
                    b.bot.forwardSpeed = 0.25F;
                    return Reaction.Outcome.RUNNING;
                }
                b.stopMoving();
                top = here;
                for (int i = 1; i <= 4; i++) {
                    BlockState st = br.world().getBlockState(top.down(i));
                    if (st.isAir() || !st.getFluidState().isEmpty()) return Reaction.Outcome.FAILED;
                    if (i <= 3 && (!b.canHarvest(st) || st.getHardness(br.world(), top.down(i)) < 0)) return Reaction.Outcome.FAILED;
                }
                begun = true;
            }
            if (leaving) return climbOut(br);
            if (dug < 3) {
                BlockPos next = top.down(dug + 1);
                if (br.world().getBlockState(next).isAir()) {
                    dug++;
                    return Reaction.Outcome.RUNNING;
                }
                b.lookAt(Vec3d.ofCenter(next));
                Body.Dig d = b.dig(next);
                if (d == Body.Dig.FAILED) return Reaction.Outcome.FAILED;
                if (d == Body.Dig.DONE) dug++;
                return Reaction.Outcome.RUNNING;
            }
            if (!sealed) {
                if (b.bot.getBlockPos().getY() > top.getY() - 3) {
                    // Still falling; or stuck on the rim: shuffle over the hole, and give up if that fails.
                    Vec3d hole = Vec3d.ofBottomCenter(top);
                    b.lookAt(hole.add(0, -1, 0));
                    b.bot.forwardSpeed = 0.3F;
                    return ++falling > 60 ? Reaction.Outcome.FAILED : Reaction.Outcome.RUNNING;
                }
                b.bot.forwardSpeed = 0F;
                if (!b.hold(Moves::buildingBlock)) {
                    sealed = true;   // nothing to seal with: sit in the hole anyway
                    return Reaction.Outcome.RUNNING;
                }
                sealed = b.placeAt(top.down());
                if (!sealed) {
                    b.lookAt(Vec3d.ofCenter(top.down()));
                    return ++climbed > 40 ? Reaction.Outcome.FAILED : Reaction.Outcome.RUNNING;
                }
                climbed = 0;
                return Reaction.Outcome.RUNNING;
            }
            b.stopMoving();
            b.bot.setSneaking(true);
            if (!stillNeeded) {
                b.bot.setSneaking(false);
                leaving = true;
            }
            return Reaction.Outcome.RUNNING;
        }

        boolean hidden() {
            return sealed && !leaving;
        }

        /** Breaks the seal and pillars up out of the hole. */
        private Reaction.Outcome climbOut(Brain br) {
            Body b = br.body;
            BlockPos seal = top.down();
            if (!br.world().getBlockState(seal).isAir()) {
                b.dig(seal);
                return Reaction.Outcome.RUNNING;
            }
            if (b.bot.getY() >= top.getY() - 0.1) return Reaction.Outcome.DONE;
            if (climbed > 6) return Reaction.Outcome.FAILED;
            if (!b.hold(Moves::buildingBlock)) return Reaction.Outcome.FAILED;
            if (b.bot.isOnGround()) {
                climbFrom = b.bot.getY();
                b.bot.setPitch(90F);
                b.bot.setJumping(true);
                return Reaction.Outcome.RUNNING;
            }
            b.bot.setJumping(false);
            if (b.bot.getY() > climbFrom + 1.0) {
                if (b.placeAt(BlockPos.ofFloored(b.bot.getX(), climbFrom, b.bot.getZ()))) climbed++;
            }
            return Reaction.Outcome.RUNNING;
        }

        void leave() {
            leaving = true;
        }
    }

    // ------------------------------------------------------------------ pillaring up

    /** Jumps and places a block under itself, n times. */
    static final class Pillar {
        private final int height;
        private int placed;
        private double from;
        private int tries;

        Pillar(int height) {
            this.height = height;
        }

        boolean done() {
            return placed >= height;
        }

        Reaction.Outcome tick(Brain br) {
            Body b = br.body;
            if (done()) return Reaction.Outcome.DONE;
            if (!b.hold(Moves::buildingBlock) || ++tries > 200) return Reaction.Outcome.FAILED;
            b.stopMoving();
            b.bot.setPitch(90F);
            if (b.bot.isOnGround()) {
                from = b.bot.getY();
                b.bot.setJumping(true);
                return Reaction.Outcome.RUNNING;
            }
            b.bot.setJumping(false);
            if (b.bot.getY() > from + 1.0 && b.placeAt(BlockPos.ofFloored(b.bot.getX(), from, b.bot.getZ()))) placed++;
            return Reaction.Outcome.RUNNING;
        }
    }

    // ------------------------------------------------------------------ lighting a torch

    /** Walks to the nearest flame it knows and lights one torch there. */
    static final class LightTorch {
        private Notebook.Mark flame;
        private int tries;

        Reaction.Outcome tick(Brain br) {
            Body b = br.body;
            if (b.has(Moves::litTorch)) return Reaction.Outcome.DONE;
            if (!b.has(Moves::unlitTorch)) return Reaction.Outcome.FAILED;
            if (flame == null) {
                flame = br.notebook.nearest(Notebook.Kind.FLAME, b.bot.getBlockPos(), 32);
                if (flame == null) return Reaction.Outcome.FAILED;
            }
            if (!stillThere(br, flame)) {
                flame = null;
                return Reaction.Outcome.RUNNING;
            }
            if (!b.inReach(flame.pos)) {
                if (b.walkTo(flame.pos, 2.5) == Body.Walk.FAILED) {
                    br.notebook.forget(flame.kind, flame.pos);
                    flame = null;
                }
                return Reaction.Outcome.RUNNING;
            }
            b.stopMoving();
            b.hold(Moves::unlitTorch);
            b.lookAt(Vec3d.ofCenter(flame.pos));
            if (tries++ % 10 == 5) {
                b.useOn(flame.pos);
                if (b.bot.currentScreenHandler != b.bot.playerScreenHandler) b.bot.closeHandledScreen();
            }
            return tries > 80 ? Reaction.Outcome.FAILED : Reaction.Outcome.RUNNING;
        }
    }
}
