package net.unbeta.content.train;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChainBlock;
import net.minecraft.block.PoweredRailBlock;
import net.minecraft.block.enums.RailShape;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.FurnaceMinecartEntity;
import net.unbeta.content.mixin.FurnaceMinecartAccessor;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.unbeta.content.mixin.LivingEntityJumpAccessor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Minecart trains and jumping.
 *
 * Right-click a cart with a chain, then another cart: they're linked (up to 9 carts a train).
 * Iron chain glues carts nose to tail and shares one speed (never too little to climb a powered
 * rail); copper chain is a rope with half a block of give, and each cart gets its own boosts.
 * A rider pressing jump hops the cart off the track; it lands with an anvil clank on a straight
 * rail it's lined up with, or derails with a thud anywhere else.
 */
public final class Trains {

    private Trains() {}

    public static ChainBlock COPPER_CHAIN;
    public static Item COPPER_CHAIN_ITEM;

    public static final int MAX_CARTS = 9;
    /** A minecart's length: distances between carts are measured end to end, not middle to middle. */
    public static final double CART = 0.98;
    /** End-to-end gap of two chained carts at rest (nearly touching). */
    public static final double GAP = 0.05;
    /** Iron chain has a little give either way before it pulls or pushes. */
    public static final double IRON_GIVE = 0.15;
    public static final double COPPER_SLACK = 0.5;
    /** Copper stretched past this (end to end) for half a second snaps. */
    public static final double COPPER_SNAP = GAP + COPPER_SLACK + 3.0;
    /** Iron only breaks when part of the train is off the track and this far from the rest. */
    public static final double IRON_BREAK = 1.0;
    private static final int SNAP_TICKS = 10;
    /** How far apart (end to end) two carts can be to chain them. */
    public static final double LINK_REACH = 3.0;
    private static final double PULL = 0.3, MAX_CORRECTION = 0.15;
    private static final double COPPER_KEEP = 0.9;
    private static final double JUMP = 1.25, FEATHER_JUMP = 2.5;
    private static final Identifier FEATHER = new Identifier("cloudboots", "golden_feather");

    /** Carts with chains that ticked this tick, per world. */
    private static final Map<ServerWorld, Set<AbstractMinecartEntity>> ACTIVE = new HashMap<>();

    private record Pending(UUID cart, int type, long time) {}

    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    /** How long each over-stretched chain has been over-stretched, by the pair of carts. */
    private static final Map<String, Integer> STRETCHED = new HashMap<>();

    private static double endGap(Entity a, Entity b) {
        return a.getPos().distanceTo(b.getPos()) - CART;
    }

    private static double correction(double x) {
        return MathHelper.clamp(PULL * x, -MAX_CORRECTION, MAX_CORRECTION);
    }

    // ------------------------------------------------------------------ setting up

    public static void register() {
        COPPER_CHAIN = Registry.register(Registries.BLOCK, new Identifier("unbeta-content", "copper_chain"),
                new ChainBlock(AbstractBlock.Settings.copy(Blocks.CHAIN)));
        COPPER_CHAIN_ITEM = Registry.register(Registries.ITEM, new Identifier("unbeta-content", "copper_chain"),
                new BlockItem(COPPER_CHAIN, new Item.Settings()));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.BUILDING_BLOCKS).register(e -> e.addAfter(Items.CHAIN, COPPER_CHAIN_ITEM));

        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!(entity instanceof AbstractMinecartEntity cart) || player.isSpectator()) return ActionResult.PASS;
            ItemStack stack = player.getStackInHand(hand);
            if (stack.isOf(Items.SHEARS)) {
                TrainCart t = (TrainCart) cart;
                boolean linked = world.isClient ? t.unbeta_linkData(0) != 0 || t.unbeta_linkData(1) != 0 : t.unbeta_hasLinks();
                if (!linked) return ActionResult.PASS;
                if (world instanceof ServerWorld sw) shear(sw, player, cart, stack, hand);
                return ActionResult.SUCCESS;
            }
            int type = chainType(stack);
            if (type == TrainCart.NONE) return ActionResult.PASS;
            if (world instanceof ServerWorld sw) clickWithChain(sw, player, cart, stack, type);
            return ActionResult.SUCCESS;
        });

        ServerTickEvents.END_WORLD_TICK.register(Trains::tick);
    }

    public static int chainType(ItemStack s) {
        return s.isOf(Items.CHAIN) ? TrainCart.IRON : s.isOf(COPPER_CHAIN_ITEM) ? TrainCart.COPPER : TrainCart.NONE;
    }

    private static Item chainItem(int type) {
        return type == TrainCart.COPPER ? COPPER_CHAIN_ITEM : Items.CHAIN;
    }

    private static void say(PlayerEntity p, String text, Formatting colour) {
        p.sendMessage(Text.literal(text).formatted(colour), true);
    }

    // ------------------------------------------------------------------ linking

    private static void clickWithChain(ServerWorld world, PlayerEntity player, AbstractMinecartEntity cart, ItemStack stack, int type) {
        Pending pending = PENDING.get(player.getUuid());
        Entity first = pending == null ? null : world.getEntity(pending.cart());
        boolean fresh = pending == null || pending.type() != type || world.getTime() - pending.time() > 200
                || !(first instanceof AbstractMinecartEntity) || first.isRemoved() || first == cart;
        if (fresh) {
            PENDING.put(player.getUuid(), new Pending(cart.getUuid(), type, world.getTime()));
            world.playSound(null, cart.getBlockPos(), SoundEvents.BLOCK_CHAIN_HIT, SoundCategory.PLAYERS, 0.8F, 1.0F);
            say(player, "Now right-click the cart to chain it to", Formatting.GRAY);
            return;
        }
        PENDING.remove(player.getUuid());
        AbstractMinecartEntity a = (AbstractMinecartEntity) first;
        TrainCart ta = (TrainCart) a, tb = (TrainCart) cart;
        if (endGap(a, cart) > LINK_REACH) {
            say(player, "Too far apart - bring the carts within 3 blocks of each other", Formatting.RED);
            return;
        }
        if (ta.unbeta_slotOf(cart.getUuid()) >= 0) {
            say(player, "Those carts are already chained", Formatting.RED);
            return;
        }
        if (ta.unbeta_freeSlot() < 0 || tb.unbeta_freeSlot() < 0) {
            say(player, "A cart can only be chained front and back", Formatting.RED);
            return;
        }
        List<AbstractMinecartEntity> trainA = train(world, a), trainB = train(world, cart);
        if (trainA.contains(cart)) {
            say(player, "Those carts are already in the same train", Formatting.RED);
            return;
        }
        if (trainA.size() + trainB.size() > MAX_CARTS) {
            say(player, "A train can be at most " + MAX_CARTS + " carts long", Formatting.RED);
            return;
        }
        ta.unbeta_setLink(ta.unbeta_freeSlot(), cart.getUuid(), type);
        tb.unbeta_setLink(tb.unbeta_freeSlot(), a.getUuid(), type);
        if (!player.getAbilities().creativeMode) stack.decrement(1);
        world.playSound(null, cart.getBlockPos(), SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.PLAYERS, 1.0F, 1.0F);
        say(player, type == TrainCart.IRON ? "Chained (iron)" : "Chained (copper)", Formatting.GREEN);
    }

    private static void shear(ServerWorld world, PlayerEntity player, AbstractMinecartEntity cart, ItemStack shears, net.minecraft.util.Hand hand) {
        TrainCart t = (TrainCart) cart;
        int best = -1;
        double bestD = Double.MAX_VALUE;
        for (int s = 0; s < 2; s++) {
            if (t.unbeta_link(s) == null) continue;
            Entity other = world.getEntity(t.unbeta_link(s));
            double d = other == null ? 1e9 : other.squaredDistanceTo(player);
            if (best < 0 || d < bestD) {
                best = s;
                bestD = d;
            }
        }
        if (best < 0) return;
        unlink(world, cart, best, true);
        shears.damage(1, player, p -> p.sendToolBreakStatus(hand));
        world.playSound(null, cart.getBlockPos(), SoundEvents.ENTITY_SHEEP_SHEAR, SoundCategory.PLAYERS, 1.0F, 1.2F);
    }

    /** Removes the link at one end of a cart (and the other cart's end of it). */
    public static void unlink(ServerWorld world, AbstractMinecartEntity cart, int slot, boolean drop) {
        TrainCart t = (TrainCart) cart;
        UUID otherId = t.unbeta_link(slot);
        if (otherId == null) return;
        int type = t.unbeta_linkType(slot);
        t.unbeta_setLink(slot, null, TrainCart.NONE);
        Entity other = world.getEntity(otherId);
        Vec3d at = cart.getPos();
        if (other instanceof TrainCart to) {
            int os = to.unbeta_slotOf(cart.getUuid());
            if (os >= 0) to.unbeta_setLink(os, null, TrainCart.NONE);
            at = at.add(other.getPos()).multiply(0.5);
        }
        if (drop) {
            ItemEntity item = new ItemEntity(world, at.x, at.y + 0.5, at.z, new ItemStack(chainItem(type)));
            item.setToDefaultPickupDelay();
            world.spawnEntity(item);
        }
    }

    public static void unlinkAll(ServerWorld world, AbstractMinecartEntity cart, boolean drop) {
        unlink(world, cart, 0, drop);
        unlink(world, cart, 1, drop);
    }

    /** The neighbour at one end, if it's loaded and still linked back. */
    private static AbstractMinecartEntity neighbour(ServerWorld world, AbstractMinecartEntity cart, int slot) {
        UUID id = ((TrainCart) cart).unbeta_link(slot);
        if (id == null) return null;
        Entity e = world.getEntity(id);
        if (e instanceof AbstractMinecartEntity m && !m.isRemoved() && ((TrainCart) m).unbeta_slotOf(cart.getUuid()) >= 0) return m;
        return null;
    }

    /** Every cart in this cart's train, end to end. */
    public static List<AbstractMinecartEntity> train(ServerWorld world, AbstractMinecartEntity start) {
        // Walk to one end...
        AbstractMinecartEntity end = start, prev = null;
        for (int guard = 0; guard < MAX_CARTS * 2; guard++) {
            AbstractMinecartEntity next = null;
            for (int s = 0; s < 2; s++) {
                AbstractMinecartEntity n = neighbour(world, end, s);
                if (n != null && n != prev) {
                    next = n;
                    break;
                }
            }
            if (next == null || next == start) break;
            prev = end;
            end = next;
        }
        // ...then collect from that end to the other.
        List<AbstractMinecartEntity> out = new ArrayList<>();
        Set<AbstractMinecartEntity> seen = new HashSet<>();
        AbstractMinecartEntity cur = end;
        prev = null;
        while (cur != null && seen.add(cur) && out.size() < MAX_CARTS * 2) {
            out.add(cur);
            AbstractMinecartEntity next = null;
            for (int s = 0; s < 2; s++) {
                AbstractMinecartEntity n = neighbour(world, cur, s);
                if (n != null && n != prev) {
                    next = n;
                    break;
                }
            }
            prev = cur;
            cur = next;
        }
        return out;
    }

    private static int linkTypeBetween(AbstractMinecartEntity a, AbstractMinecartEntity b) {
        TrainCart t = (TrainCart) a;
        int s = t.unbeta_slotOf(b.getUuid());
        return s < 0 ? TrainCart.NONE : t.unbeta_linkType(s);
    }

    // ------------------------------------------------------------------ the train, every tick

    public static void track(ServerWorld world, AbstractMinecartEntity cart) {
        ACTIVE.computeIfAbsent(world, w -> new LinkedHashSet<>()).add(cart);
    }

    private static void tick(ServerWorld world) {
        Set<AbstractMinecartEntity> active = ACTIVE.remove(world);
        if (active == null) return;
        // Tidy up links: a missing neighbour for 5 s lets go (no chain drops, so nothing is duplicated).
        for (AbstractMinecartEntity cart : active) {
            if (cart.isRemoved()) continue;
            TrainCart t = (TrainCart) cart;
            for (int s = 0; s < 2; s++) {
                if (t.unbeta_link(s) == null) continue;
                Entity other = world.getEntity(t.unbeta_link(s));
                if (other instanceof TrainCart to && !other.isRemoved()) {
                    if (to.unbeta_slotOf(cart.getUuid()) < 0) {
                        t.unbeta_setLink(s, null, TrainCart.NONE);
                        continue;
                    }
                    t.unbeta_setMissing(s, 0);
                    t.unbeta_setLinkData(s, other.getId() * 4 + t.unbeta_linkType(s));
                } else {
                    t.unbeta_setLinkData(s, 0);
                    t.unbeta_setMissing(s, t.unbeta_missing(s) + 1);
                    if (t.unbeta_missing(s) > 100) t.unbeta_setLink(s, null, TrainCart.NONE);
                }
            }
        }
        Set<AbstractMinecartEntity> done = new HashSet<>();
        for (AbstractMinecartEntity cart : active) {
            if (cart.isRemoved() || done.contains(cart)) continue;
            List<AbstractMinecartEntity> carts = train(world, cart);
            done.addAll(carts);
            if (carts.size() >= 2) step(world, carts);
        }
    }

    private static Vec3d flat(Vec3d v) {
        return new Vec3d(v.x, 0, v.z);
    }

    private static boolean offTrack(AbstractMinecartEntity c) {
        TrainCart t = (TrainCart) c;
        return t.unbeta_airborne() || t.unbeta_derailed();
    }

    private static void step(ServerWorld world, List<AbstractMinecartEntity> carts) {
        int n = carts.size();

        // 1. Snapping. Copper: stretched ~3 blocks too far for half a second. Iron: only when a jump
        //    leaves part of the train off the track and that part pulls away from the rest.
        for (int i = 1; i < n; i++) {
            AbstractMinecartEntity a = carts.get(i - 1), b = carts.get(i);
            int type = linkTypeBetween(a, b);
            double d = endGap(a, b);
            String key = a.getUuid().compareTo(b.getUuid()) < 0 ? a.getUuid() + "|" + b.getUuid() : b.getUuid() + "|" + a.getUuid();
            boolean snap;
            if (type == TrainCart.IRON) {
                snap = offTrack(a) != offTrack(b) && d > IRON_BREAK;
            } else {
                if (d <= COPPER_SNAP) {
                    STRETCHED.remove(key);
                    continue;
                }
                snap = STRETCHED.merge(key, 1, Integer::sum) >= SNAP_TICKS;
            }
            if (snap) {
                STRETCHED.remove(key);
                unlink(world, a, ((TrainCart) a).unbeta_slotOf(b.getUuid()), true);
                world.playSound(null, a.getBlockPos(), SoundEvents.BLOCK_CHAIN_BREAK, SoundCategory.NEUTRAL, 1.0F, 0.8F);
                return;
            }
        }
        double max = 0.52;      // Unbeta's minecart top speed

        // 2. Iron groups move as one: every cart gets the same speed along the train. If any cart in
        //    the group is on a working powered rail, the whole group gets that cart's boosted speed.
        //    Directions follow the train's own shape (towards cart 0), so nothing flips.
        Vec3d[] along = new Vec3d[n];
        for (int i = 0; i < n; i++) {
            Vec3d front = carts.get(Math.max(0, i - 1)).getPos(), back = carts.get(Math.min(n - 1, i + 1)).getPos();
            Vec3d t = flat(front.subtract(back));
            along[i] = t.lengthSquared() < 1.0E-6 ? Vec3d.ZERO : t.normalize();
        }
        int start = 0;
        while (start < n) {
            int end = start;
            while (end < n - 1 && linkTypeBetween(carts.get(end), carts.get(end + 1)) == TrainCart.IRON) end++;
            if (end > start) {
                double sum = 0, boost = 0;
                for (int i = start; i <= end; i++) {
                    double s = flat(carts.get(i).getVelocity()).dotProduct(along[i]);
                    sum += s;
                    if (onPoweredRail(world, carts.get(i)) && Math.abs(s) > Math.abs(boost)) boost = s;
                }
                double speed = sum / (end - start + 1);
                if (boost != 0 && Math.abs(boost) > Math.abs(speed)) speed = Math.abs(boost) * (speed != 0 ? Math.signum(speed) : Math.signum(boost));
                speed = MathHelper.clamp(speed, -max, max);
                for (int i = start; i <= end; i++) {
                    AbstractMinecartEntity c = carts.get(i);
                    Vec3d v = c.getVelocity();
                    c.setVelocity(along[i].x * speed, v.y, along[i].z * speed);
                    c.velocityModified = true;
                }
            }
            start = end + 1;
        }

        // 3. Copper links: a rope. The cart behind is dragged once the slack is used up.
        List<AbstractMinecartEntity> ordered = carts;
        Vec3d total = Vec3d.ZERO;
        for (AbstractMinecartEntity c : carts) total = total.add(flat(c.getVelocity()));
        if (total.lengthSquared() > 1.0E-6 && total.dotProduct(flat(carts.get(0).getPos().subtract(carts.get(n - 1).getPos()))) < 0) {
            ordered = new ArrayList<>(carts);
            java.util.Collections.reverse(ordered);
        }
        for (int i = 1; i < n; i++) {
            AbstractMinecartEntity ahead = ordered.get(i - 1), c = ordered.get(i);
            if (linkTypeBetween(ahead, c) != TrainCart.COPPER) continue;
            Vec3d toward = flat(ahead.getPos().subtract(c.getPos()));
            if (toward.lengthSquared() < 1.0E-8) continue;
            toward = toward.normalize();
            double d = endGap(ahead, c);
            if (d <= GAP + COPPER_SLACK) continue;      // slack: each cart rolls on its own
            Vec3d v = c.getVelocity();
            double own = flat(v).dotProduct(toward);
            double sp = MathHelper.clamp(Math.max(own, COPPER_KEEP * ahead.getVelocity().horizontalLength()) + correction(d - GAP - COPPER_SLACK), -max, max);
            c.setVelocity(toward.x * sp, v.y, toward.z * sp);
            c.velocityModified = true;
        }

        // 3b. Pushing: copper-chained carts that are touching push each other like unchained carts
        //     do (vanilla's own bumping is off between chained carts), so a furnace cart can push.
        for (int i = 1; i < n; i++) {
            AbstractMinecartEntity a = carts.get(i - 1), b = carts.get(i);
            if (linkTypeBetween(a, b) != TrainCart.COPPER || offTrack(a) || offTrack(b)) continue;
            if (endGap(a, b) > GAP + 0.05) continue;
            Vec3d dir = flat(a.getPos().subtract(b.getPos()));
            if (dir.lengthSquared() < 1.0E-8) continue;
            dir = dir.normalize();                       // from b towards a
            double sa = flat(a.getVelocity()).dotProduct(dir), sb = flat(b.getVelocity()).dotProduct(dir);
            if (sb > sa) {                               // b is driving into a: a goes at least as fast
                a.setVelocity(a.getVelocity().add(dir.multiply(sb - sa)));
                a.velocityModified = true;
            } else if (sa < 0 && sa < sb) {              // a is backing into b: b goes at least as fast backwards
                b.setVelocity(b.getVelocity().add(dir.multiply(sa - sb)));
                b.velocityModified = true;
            }
        }

        // 4. Hard limits, by moving the carts themselves: chained carts can never overlap (so never
        //    pass each other), and iron-chained carts never drift further apart than the give allows.
        for (int pass = 0; pass < 3; pass++) {
            for (int i = 1; i < n; i++) {
                AbstractMinecartEntity a = carts.get(i - 1), b = carts.get(i);
                if (offTrack(a) || offTrack(b)) continue;
                Vec3d ab = flat(a.getPos().subtract(b.getPos()));
                double centres = ab.length();
                if (centres < 1.0E-4) continue;
                Vec3d dir = ab.multiply(1 / centres);
                double gap = centres - CART;
                double fix = 0;
                if (gap < 0) fix = -gap;                                         // overlapping: push apart
                else if (linkTypeBetween(a, b) == TrainCart.IRON && gap > GAP + IRON_GIVE) fix = Math.max(-0.1, (GAP + IRON_GIVE) - gap);   // pull together, a little at a time
                if (fix == 0) continue;
                Vec3d half = dir.multiply(fix / 2);
                a.setPosition(a.getX() + half.x, a.getY(), a.getZ() + half.z);
                b.setPosition(b.getX() - half.x, b.getY(), b.getZ() - half.z);
                if (gap < 0) {
                    // and stop them closing any further
                    double closing = flat(b.getVelocity()).dotProduct(dir) - flat(a.getVelocity()).dotProduct(dir);
                    if (closing > 0) {
                        Vec3d push = dir.multiply(closing / 2);
                        a.setVelocity(a.getVelocity().add(push));
                        b.setVelocity(b.getVelocity().subtract(push));
                        a.velocityModified = true;
                        b.velocityModified = true;
                    }
                }
            }
        }
    }

    /** One furnace cart was fuelled: every furnace cart in its train now pushes the same way along it. */
    public static void pointFurnaces(ServerWorld world, FurnaceMinecartEntity clicked) {
        if (!((TrainCart) clicked).unbeta_hasLinks()) return;
        List<AbstractMinecartEntity> carts = train(world, clicked);
        int n = carts.size(), at = carts.indexOf(clicked);
        if (n < 2 || at < 0) return;
        FurnaceMinecartAccessor fc = (FurnaceMinecartAccessor) clicked;
        Vec3d push = new Vec3d(fc.unbeta_pushX(), 0, fc.unbeta_pushZ());
        if (push.lengthSquared() < 1.0E-8) return;
        double way = Math.signum(push.dotProduct(along(carts, at)));
        if (way == 0) return;
        for (int i = 0; i < n; i++) {
            if (i == at || !(carts.get(i) instanceof FurnaceMinecartEntity f)) continue;
            Vec3d t = along(carts, i).multiply(way);
            FurnaceMinecartAccessor fa = (FurnaceMinecartAccessor) f;
            fa.unbeta_setPushX(t.x);
            fa.unbeta_setPushZ(t.z);
        }
    }

    /** The direction along the train at cart i, pointing towards cart 0. */
    private static Vec3d along(List<AbstractMinecartEntity> carts, int i) {
        int n = carts.size();
        Vec3d t = flat(carts.get(Math.max(0, i - 1)).getPos().subtract(carts.get(Math.min(n - 1, i + 1)).getPos()));
        return t.lengthSquared() < 1.0E-8 ? Vec3d.ZERO : t.normalize();
    }

    private static BlockPos railPos(AbstractMinecartEntity c) {
        BlockPos p = BlockPos.ofFloored(c.getX(), c.getY(), c.getZ());
        if (c.getWorld().getBlockState(p.down()).getBlock() instanceof AbstractRailBlock) p = p.down();
        return p;
    }

    private static boolean onPoweredRail(ServerWorld world, AbstractMinecartEntity c) {
        if (!((TrainCart) c).unbeta_onRail()) return false;
        BlockState s = world.getBlockState(railPos(c));
        return s.isOf(Blocks.POWERED_RAIL) && s.get(PoweredRailBlock.POWERED);
    }

    // ------------------------------------------------------------------ jumping

    public static boolean riderJumping(AbstractMinecartEntity cart) {
        return cart.getFirstPassenger() instanceof PlayerEntity p && ((LivingEntityJumpAccessor) p).unbeta_isJumping();
    }

    private static boolean holdsFeather(PlayerEntity p) {
        return Registries.ITEM.getId(p.getMainHandStack().getItem()).equals(FEATHER)
                || Registries.ITEM.getId(p.getOffHandStack().getItem()).equals(FEATHER);
    }

    /** The cart and every cart glued to it by iron chain. */
    private static List<AbstractMinecartEntity> ironGroup(ServerWorld world, AbstractMinecartEntity cart) {
        List<AbstractMinecartEntity> all = train(world, cart);
        int at = all.indexOf(cart);
        if (at < 0) return List.of(cart);
        int lo = at, hi = at;
        while (lo > 0 && linkTypeBetween(all.get(lo - 1), all.get(lo)) == TrainCart.IRON) lo--;
        while (hi < all.size() - 1 && linkTypeBetween(all.get(hi), all.get(hi + 1)) == TrainCart.IRON) hi++;
        return all.subList(lo, hi + 1);
    }

    public static void jump(AbstractMinecartEntity cart) {
        if (!(cart.getWorld() instanceof ServerWorld world)) return;
        PlayerEntity rider = (PlayerEntity) cart.getFirstPassenger();
        List<AbstractMinecartEntity> group = ironGroup(world, cart);
        double height = (holdsFeather(rider) ? FEATHER_JUMP : JUMP) / Math.pow(2, group.size() - 1);
        double vy = launchSpeed(height);
        for (AbstractMinecartEntity c : group) {
            TrainCart t = (TrainCart) c;
            if (t.unbeta_airborne()) continue;
            t.unbeta_setAirborne(true);
            t.unbeta_setDerailed(false);
            c.setOnGround(false);
            Vec3d v = c.getVelocity();
            c.setVelocity(v.x, vy, v.z);
            c.velocityModified = true;
        }
        world.playSound(null, cart.getBlockPos(), SoundEvents.ENTITY_IRON_GOLEM_STEP, SoundCategory.NEUTRAL, 0.6F, 1.4F);
    }

    /** Upward speed that tops out at this height under minecart gravity and air drag. */
    /** Extra pull downwards in the air, on top of a minecart's own: a short, snappy hop. */
    public static final double AIR_GRAVITY = 0.06;

    private static double launchSpeed(double height) {
        double lo = 0, hi = 3;
        for (int it = 0; it < 40; it++) {
            double mid = (lo + hi) / 2;
            if (apex(mid) < height) lo = mid;
            else hi = mid;
        }
        return hi;
    }

    /** The same steps the cart takes each tick in the air (vanilla gravity, move, drag, then ours). */
    private static double apex(double v0) {
        double y = 0, v = v0, top = 0;
        for (int t = 0; t < 200; t++) {
            v -= 0.04;
            y += v;
            v *= 0.95;
            v -= AIR_GRAVITY;
            if (y > top) top = y;
            if (v < 0 && y < top) break;
        }
        return top;
    }

    /** Each tick in the air: fall faster, and keep the speed the cart took off with. */
    public static void airTick(AbstractMinecartEntity cart) {
        Vec3d v = cart.getVelocity();
        double max = 0.52;
        double x = MathHelper.clamp(v.x / 0.95, -max, max), z = MathHelper.clamp(v.z / 0.95, -max, max);
        cart.setVelocity(x, v.y - AIR_GRAVITY, z);
        cart.velocityModified = true;
    }

    /** After moving through the air: has it come down yet, and how? */
    public static void checkLanding(AbstractMinecartEntity cart, int airTicks) {
        TrainCart t = (TrainCart) cart;
        ServerWorld world = (ServerWorld) cart.getWorld();
        if (cart.isTouchingWater() || airTicks > 200) {
            t.unbeta_setAirborne(false);
            return;
        }
        if (airTicks < 2 || cart.getVelocity().y > 0) return;
        BlockPos rp = railPos(cart);
        BlockState rail = world.getBlockState(rp);
        if (rail.getBlock() instanceof AbstractRailBlock railBlock && cart.getY() - rp.getY() < 0.5) {
            t.unbeta_setAirborne(false);
            RailShape shape = rail.get(railBlock.getShapeProperty());
            if (linedUp(shape, cart.getVelocity())) {
                world.playSound(null, cart.getBlockPos(), SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.NEUTRAL, 0.5F, 1.0F);
            } else {
                t.unbeta_setDerailed(true);
                world.playSound(null, cart.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.NEUTRAL, 1.0F, 1.0F);
            }
            return;
        }
        if (cart.isOnGround()) {
            t.unbeta_setAirborne(false);
            world.playSound(null, cart.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.NEUTRAL, 1.0F, 1.0F);
        }
    }

    private static boolean linedUp(RailShape shape, Vec3d v) {
        double sp = v.horizontalLength();
        if (sp < 0.02) return true;
        return switch (shape) {
            case NORTH_SOUTH, ASCENDING_NORTH, ASCENDING_SOUTH -> Math.abs(v.z) / sp > 0.9;
            case EAST_WEST, ASCENDING_EAST, ASCENDING_WEST -> Math.abs(v.x) / sp > 0.9;
            default -> false;      // a curve: can't land on that
        };
    }

    /** A derailed cart goes back to normal once it's off the rails or has stopped. */
    public static void checkRerail(AbstractMinecartEntity cart) {
        BlockState s = cart.getWorld().getBlockState(railPos(cart));
        if (!(s.getBlock() instanceof AbstractRailBlock) || cart.getVelocity().horizontalLength() < 0.01) {
            ((TrainCart) cart).unbeta_setDerailed(false);
        }
    }
}
