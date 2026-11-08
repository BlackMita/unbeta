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
    /** A cart that's off the track (jumping, derailed) breaks away once it's this far from the next. */
    public static final double BREAK_AWAY = 1.0;
    /** Where an iron stretch meets a copper stretch, the link breaks once they're this far apart for half a second. */
    public static final double JUNCTION_BREAK = 1.0;
    private static final int SNAP_TICKS = 10;
    /** How far apart (end to end) two carts can be to chain them. */
    public static final double LINK_REACH = 3.0;
    private static final double JUMP = 1.25, FEATHER_JUMP = 2.5;
    private static final Identifier FEATHER = new Identifier("cloudboots", "golden_feather");

    /** Carts with chains that ticked this tick, per world. */
    private static final Map<ServerWorld, Set<AbstractMinecartEntity>> ACTIVE = new HashMap<>();

    private record Pending(UUID cart, int type, long time) {}

    /** How far you can walk from a cart you're holding by its chain before it starts to follow. */
    public static final double HOLD_SLACK = 3.0, HOLD_LIMIT = 12.0;

    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    /** How long each over-stretched chain has been over-stretched, by the pair of carts. */
    private static final Map<String, Integer> STRETCHED = new HashMap<>();

    private static double endGap(Entity a, Entity b) {
        return a.getPos().distanceTo(b.getPos()) - CART;
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
        ServerTickEvents.END_SERVER_TICK.register(server -> holding(server));
    }

    /** Stop holding whatever cart this player had by its chain. */
    private static void letGo(ServerWorld world, UUID player) {
        Pending p = PENDING.remove(player);
        if (p == null) return;
        for (ServerWorld w : world.getServer().getWorlds()) {
            if (w.getEntity(p.cart()) instanceof TrainCart t) t.unbeta_setHeldData(0);
        }
    }

    /** Carts held by a chain: shown hanging from your hand, and dragged along when you walk off. */
    private static void holding(net.minecraft.server.MinecraftServer server) {
        if (PENDING.isEmpty()) return;
        for (UUID id : new ArrayList<>(PENDING.keySet())) {
            Pending p = PENDING.get(id);
            PlayerEntity player = server.getPlayerManager().getPlayer(id);
            ServerWorld world = player == null ? null : (ServerWorld) player.getWorld();
            Entity e = world == null ? null : world.getEntity(p.cart());
            if (player == null || !(e instanceof AbstractMinecartEntity cart) || cart.isRemoved() || player.isSpectator()) {
                PENDING.remove(id);
                if (e instanceof TrainCart t) t.unbeta_setHeldData(0);
                continue;
            }
            boolean stillHolding = chainType(player.getMainHandStack()) == p.type() || chainType(player.getOffHandStack()) == p.type();
            double dist = cart.getPos().add(0, 0.4, 0).distanceTo(player.getPos().add(0, 1.0, 0));
            if (!stillHolding || dist > HOLD_LIMIT) {
                letGo(world, id);
                world.playSound(null, cart.getBlockPos(), SoundEvents.BLOCK_CHAIN_FALL, SoundCategory.PLAYERS, 0.8F, 1.0F);
                continue;
            }
            ((TrainCart) cart).unbeta_setHeldData(player.getId() * 4 + p.type());
            if (dist > HOLD_SLACK) {
                // Dragged by the chain: like copper, the cart is pulled towards you once it's taut.
                Vec3d toward = flat(player.getPos().subtract(cart.getPos()));
                if (toward.lengthSquared() < 1.0E-6) continue;
                toward = toward.normalize();
                Vec3d v = cart.getVelocity();
                double own = flat(v).dotProduct(toward);
                double sp = MathHelper.clamp(Math.max(own, 0.08 + 0.12 * (dist - HOLD_SLACK)), -0.52, 0.52);
                cart.setVelocity(toward.x * sp, v.y, toward.z * sp);
                cart.velocityModified = true;
                if (cart instanceof FurnaceMinecartEntity) ((TrainCart) cart).unbeta_setDragged(5);
            }
        }
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
        if (first == cart) {                    // clicked the cart you're already holding: let go
            letGo(world, player.getUuid());
            world.playSound(null, cart.getBlockPos(), SoundEvents.BLOCK_CHAIN_STEP, SoundCategory.PLAYERS, 0.8F, 1.0F);
            say(player, "Let go of the cart", Formatting.GRAY);
            return;
        }
        boolean fresh = pending == null || pending.type() != type || !(first instanceof AbstractMinecartEntity) || first.isRemoved();
        if (fresh) {
            letGo(world, player.getUuid());
            PENDING.put(player.getUuid(), new Pending(cart.getUuid(), type, world.getTime()));
            world.playSound(null, cart.getBlockPos(), SoundEvents.BLOCK_CHAIN_HIT, SoundCategory.PLAYERS, 0.8F, 1.0F);
            say(player, "Holding the cart by its chain - right-click another cart to link them", Formatting.GRAY);
            return;
        }
        letGo(world, player.getUuid());
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
        TRAILS_USED.clear();
        Set<AbstractMinecartEntity> done = new HashSet<>();
        for (AbstractMinecartEntity cart : active) {
            if (cart.isRemoved() || done.contains(cart)) continue;
            List<AbstractMinecartEntity> carts = train(world, cart);
            done.addAll(carts);
            if (carts.size() >= 2) step(world, carts);
        }
        TRAILS.keySet().removeIf(id -> !TRAILS_USED.contains(id) && world.getEntity(id) != null);
    }

    private static Vec3d flat(Vec3d v) {
        return new Vec3d(v.x, 0, v.z);
    }

    private static boolean offTrack(AbstractMinecartEntity c) {
        TrainCart t = (TrainCart) c;
        return t.unbeta_airborne() || t.unbeta_derailed();
    }

    /**
     * How a train moves.
     *
     * A train is split into stretches: runs of carts chained with the same kind of chain. Each
     * stretch moves as one solid piece. Its front cart rolls normally, with the stretch's speed, and
     * the carts behind are set down along the exact track the front cart just covered, one
     * cart-length apart, so they can't overlap, pass each other or end up inside a block.
     *
     *  - Iron stretch: its speed is the average of its carts' (a furnace cart holds it back).
     *  - Copper stretch: the same, but a working powered rail under any of its carts speeds the
     *    whole stretch up, furnace carts included.
     *
     * Where an iron stretch meets a copper stretch, nothing pulls: if the front stretch runs away
     * from the one behind (say a boosted copper front and a furnace-slowed iron back), the link
     * between them breaks. A stretch running into the one ahead pushes it along.
     */
    private static final Map<UUID, java.util.ArrayDeque<Vec3d>> TRAILS = new HashMap<>();
    private static final Set<UUID> TRAILS_USED = new HashSet<>();
    private static final double SPACING = CART + GAP;
    /** Furthest a following cart is moved in one tick (more than a cart's top speed). */
    private static final double FOLLOW_STEP = 0.6;
    private static final double TOP_SPEED = 0.52;     // Unbeta's minecart top speed
    private static final double FURNACE_SPEED = 0.2;  // a furnace minecart's own top speed

    private static String pairKey(AbstractMinecartEntity a, AbstractMinecartEntity b) {
        return a.getUuid().compareTo(b.getUuid()) < 0 ? a.getUuid() + "|" + b.getUuid() : b.getUuid() + "|" + a.getUuid();
    }

    private static void snap(ServerWorld world, AbstractMinecartEntity a, AbstractMinecartEntity b) {
        STRETCHED.remove(pairKey(a, b));
        unlink(world, a, ((TrainCart) a).unbeta_slotOf(b.getUuid()), true);
        world.playSound(null, a.getBlockPos(), SoundEvents.BLOCK_CHAIN_BREAK, SoundCategory.NEUTRAL, 1.0F, 0.8F);
    }

    private static void step(ServerWorld world, List<AbstractMinecartEntity> carts) {
        int n = carts.size();

        // The train's own direction at each cart (pointing towards cart 0), so a train bent round a
        // curve or a U-turn still agrees with itself about which way is forwards.
        Vec3d[] along = new Vec3d[n];
        for (int i = 0; i < n; i++) {
            Vec3d front = carts.get(Math.max(0, i - 1)).getPos(), back = carts.get(Math.min(n - 1, i + 1)).getPos();
            Vec3d t = flat(front.subtract(back));
            along[i] = t.lengthSquared() < 1.0E-6 ? Vec3d.ZERO : t.normalize();
        }
        double[] speed = new double[n];         // each cart's speed along the train (+ = towards cart 0)
        double heading = 0;
        for (int i = 0; i < n; i++) {
            speed[i] = flat(carts.get(i).getVelocity()).dotProduct(along[i]);
            heading += speed[i];
        }
        // Front to back, in the direction of travel (if it's barely moving, keep last tick's front).
        boolean towardZero;
        if (heading > 0.002) towardZero = true;
        else if (heading < -0.002) towardZero = false;
        else towardZero = !TRAILS.containsKey(carts.get(n - 1).getUuid());
        int[] order = new int[n];
        for (int k = 0; k < n; k++) order[k] = towardZero ? k : n - 1 - k;

        // Stretches: split wherever the kind of chain changes.
        List<int[]> stretches = new ArrayList<>();      // {first k, last k} in travel order
        List<Integer> kinds = new ArrayList<>();
        // (The link where the kind changes is where two stretches meet; the stretch behind it takes
        // its kind from the links behind that.)
        int[] link = new int[n];                     // link[k]: the chain between travel positions k-1 and k
        for (int k = 1; k < n; k++) link[k] = linkTypeBetween(carts.get(order[k - 1]), carts.get(order[k]));
        int startK = 0, kind = link[1];
        for (int k = 2; k < n; k++) {
            if (link[k] == kind) continue;
            stretches.add(new int[]{startK, k - 1});
            kinds.add(kind);
            startK = k;
            kind = k + 1 < n ? link[k + 1] : link[k];
        }
        stretches.add(new int[]{startK, n - 1});
        kinds.add(kind);

        // 1. Breaking away.
        for (int k = 1; k < n; k++) {
            AbstractMinecartEntity a = carts.get(order[k - 1]), b = carts.get(order[k]);
            if (offTrack(a) != offTrack(b) && endGap(a, b) > BREAK_AWAY) {
                snap(world, a, b);
                return;
            }
        }
        for (int g = 1; g < stretches.size(); g++) {
            AbstractMinecartEntity a = carts.get(order[stretches.get(g - 1)[1]]), b = carts.get(order[stretches.get(g)[0]]);
            String key = pairKey(a, b);
            if (endGap(a, b) <= JUNCTION_BREAK) {
                STRETCHED.remove(key);
            } else if (STRETCHED.merge(key, 1, Integer::sum) >= SNAP_TICKS) {
                snap(world, a, b);
                return;
            }
        }

        // 2. Each stretch's speed (signed along the train).
        double sign = towardZero ? 1 : -1;
        double[] stretchSpeed = new double[stretches.size()];
        for (int g = 0; g < stretches.size(); g++) {
            int[] st = stretches.get(g);
            double sum = 0, boost = 0;
            for (int k = st[0]; k <= st[1]; k++) {
                int i = order[k];
                sum += speed[i];
                if (kinds.get(g) == TrainCart.COPPER && onPoweredRail(world, carts.get(i)) && speed[i] * sign > boost) boost = speed[i] * sign;
            }
            double sp = sum / (st[1] - st[0] + 1);
            if (boost > sp * sign) sp = boost * sign;
            double cap = TOP_SPEED;
            if (kinds.get(g) == TrainCart.IRON) {
                // An iron stretch with a furnace cart in it never goes faster than the furnace cart can.
                for (int k = st[0]; k <= st[1]; k++) if (carts.get(order[k]) instanceof FurnaceMinecartEntity) cap = FURNACE_SPEED;
            }
            stretchSpeed[g] = MathHelper.clamp(sp, -cap, cap);
        }
        // A stretch running into the one ahead of it pushes it along (back to front).
        for (int g = stretches.size() - 1; g >= 1; g--) {
            AbstractMinecartEntity a = carts.get(order[stretches.get(g - 1)[1]]), b = carts.get(order[stretches.get(g)[0]]);
            if (endGap(a, b) <= GAP + 0.05 && stretchSpeed[g] * sign > stretchSpeed[g - 1] * sign) stretchSpeed[g - 1] = stretchSpeed[g];
        }

        // 3. Move each stretch as one piece.
        for (int g = 0; g < stretches.size(); g++) {
            int[] st = stretches.get(g);
            moveStretch(carts, along, order, st[0], st[1], stretchSpeed[g]);
        }
    }

    /** One stretch, travel-order positions first..last, all going at this speed (+ = towards cart 0). */
    private static void moveStretch(List<AbstractMinecartEntity> carts, Vec3d[] along, int[] order, int first, int last, double sp) {
        for (int k = first; k <= last; k++) {
            if (offTrack(carts.get(order[k]))) {
                // In the air (or derailed) the carts fly on their own; the trail starts afresh after.
                for (int j = first; j <= last; j++) TRAILS.remove(carts.get(order[j]).getUuid());
                return;
            }
        }
        for (int k = first; k <= last; k++) {
            AbstractMinecartEntity c = carts.get(order[k]);
            // Going faster than a furnace cart can by itself: it's being carried along.
            if (c instanceof FurnaceMinecartEntity && Math.abs(sp) > FURNACE_SPEED) ((TrainCart) c).unbeta_setDragged(5);
            Vec3d v = c.getVelocity();
            Vec3d a = along[order[k]];
            c.setVelocity(a.x * sp, v.y, a.z * sp);
            c.velocityModified = true;
        }
        if (last == first) return;

        // The front cart's trail: rebuilt from where the carts are if it's new, else extended.
        AbstractMinecartEntity leader = carts.get(order[first]);
        UUID key = leader.getUuid();
        java.util.ArrayDeque<Vec3d> trail = TRAILS.get(key);
        for (int k = first + 1; k <= last; k++) TRAILS.remove(carts.get(order[k]).getUuid());
        if (trail == null) {
            trail = new java.util.ArrayDeque<>();
            for (int k = first; k <= last; k++) trail.addLast(carts.get(order[k]).getPos());
            TRAILS.put(key, trail);
        } else if (trail.peekFirst() == null || trail.peekFirst().squaredDistanceTo(leader.getPos()) > 1.0E-6) {
            trail.addFirst(leader.getPos());
        }
        TRAILS_USED.add(key);

        // Set each following cart down along the trail, one cart-length apart.
        for (int k = first + 1; k <= last; k++) {
            AbstractMinecartEntity c = carts.get(order[k]);
            Vec3d target = pointAlong(trail, (k - first) * SPACING);
            if (target == null) continue;
            Vec3d move = target.subtract(c.getPos());
            double len = move.length();
            if (len > FOLLOW_STEP) target = c.getPos().add(move.multiply(FOLLOW_STEP / len));
            if (len > 1.0E-4) c.setPosition(target.x, target.y, target.z);
        }
        trim(trail, (last - first) * SPACING + 2.0);
    }

    /** Drops the end of a trail beyond this length. */
    private static void trim(java.util.ArrayDeque<Vec3d> trail, double keep) {
        double walked = 0;
        Vec3d prev = null;
        int count = 0;
        for (Vec3d p : trail) {
            if (prev != null) walked += p.distanceTo(prev);
            prev = p;
            count++;
            if (walked > keep) break;
        }
        while (trail.size() > count) trail.removeLast();
    }

    /** The point this far back along a trail (most recent point first), or null if it's too short. */
    private static Vec3d pointAlong(java.util.ArrayDeque<Vec3d> trail, double distance) {
        if (trail == null) return null;
        Vec3d prev = null;
        double walked = 0;
        for (Vec3d p : trail) {
            if (prev != null) {
                double seg = p.distanceTo(prev);
                if (walked + seg >= distance && seg > 1.0E-6) return prev.add(p.subtract(prev).multiply((distance - walked) / seg));
                walked += seg;
            }
            prev = p;
        }
        return null;
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
