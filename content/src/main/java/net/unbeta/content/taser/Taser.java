package net.unbeta.content.taser;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LightBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/**
 * Taser lines and stuns.
 *
 * <p>A line runs from a player's taser to a living thing or a copper block. While it holds (the
 * holder keeps the taser in hand, stays within 12 blocks, and the battery lasts) electricity flows:
 * <ul>
 *   <li>the target is stunned (a creeper hit directly becomes charged; a skeleton takes the darts
 *       and nothing happens);</li>
 *   <li>copper blocks carry it: a copper block the darts hit, or any copper block a stunned thing
 *       touches, energizes every copper block joined to it face to face;</li>
 *   <li>anything touching an energized copper block, or touching a stunned thing, is stunned too,
 *       and so on outward.</li>
 * </ul>
 * Stunned things can't move or attack, shake, buzz and glow like a redstone torch. Nothing is
 * ever hurt, and a stunned player can do nothing at all - not even let go of their own taser. Water stops it all: if the holder or the target touches water, nothing happens, and
 * anything standing in water can't be stunned. Skeletons are never stunned and don't pass it on.
 */
public final class Taser {

    private Taser() {}

    public static final Identifier BUZZ_ID = new Identifier("unbeta-content", "taser_buzz");
    public static SoundEvent BUZZ;
    public static Item TASER;
    public static final TagKey<Block> CONDUCTS = TagKey.of(RegistryKeys.BLOCK, new Identifier("unbeta-content", "conducts_taser"));

    /** Who can never be stunned, beyond skeletons, Creative and Spectator players (Hunger Games adds out players). */
    public static Predicate<Entity> IMMUNE = e -> false;

    static final double REACH = 12.0;          // the darts' range
    static final double SNAP = 12.0;           // the line breaks beyond this
    private static final int MAX_STUNNED = 64;
    private static final int MAX_COPPER = 512;
    private static final int MAX_COPPER_LIGHTS = 48;
    private static final int GLOW = 7;          // a redstone torch

    private static final class Line {
        final ServerWorld world;
        final Entity target;      // or null
        final BlockPos block;     // or null
        final Hand hand;

        Line(ServerWorld world, Entity target, BlockPos block, Hand hand) {
            this.world = world;
            this.target = target;
            this.block = block;
            this.hand = hand;
        }

        Vec3d end() {
            return target != null ? target.getPos().add(0, target.getHeight() * 0.6, 0) : Vec3d.ofCenter(block);
        }
    }

    private static final class Stun {
        Vec3d anchor;
        long last;
        int sound;

        Stun(Vec3d anchor, long last) {
            this.anchor = anchor;
            this.last = last;
        }
    }

    private static final Map<UUID, Line> LINES = new HashMap<>();
    private static final Map<Entity, Stun> STUNNED = new WeakHashMap<>();
    private static final Map<BlockPos, ServerWorld> LIGHTS = new HashMap<>();

    private static final UUID SLOW_ID = UUID.fromString("0d6c2a41-5d8e-4f0b-b8c4-6a9d1f2e7b33");
    private static final EntityAttributeModifier STILL = new EntityAttributeModifier(SLOW_ID, "Tased", -1.0,
            EntityAttributeModifier.Operation.MULTIPLY_TOTAL);

    public static void register() {
        BUZZ = Registry.register(Registries.SOUND_EVENT, BUZZ_ID, SoundEvent.of(BUZZ_ID));
        TASER = Registry.register(Registries.ITEM, new Identifier("unbeta-content", "taser"),
                new TaserItem(new Item.Settings().maxDamage(TaserItem.BATTERY_TICKS)));
        net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.modifyEntriesEvent(net.minecraft.item.ItemGroups.TOOLS)
                .register(entries -> entries.add(TASER));
        ServerTickEvents.END_SERVER_TICK.register(Taser::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
            for (Map.Entry<BlockPos, ServerWorld> e : LIGHTS.entrySet()) unlight(e.getValue(), e.getKey());
            LIGHTS.clear();
            for (Entity e : new ArrayList<>(STUNNED.keySet())) release(e);
            STUNNED.clear();
            LINES.clear();
        });
        // A stunned player can't attack, mine, build or use anything - their own taser included.
        AttackEntityCallback.EVENT.register((p, w, h, e, hit) -> isStunned(p) ? ActionResult.FAIL : ActionResult.PASS);
        AttackBlockCallback.EVENT.register((p, w, h, pos, dir) -> isStunned(p) ? ActionResult.FAIL : ActionResult.PASS);
        UseBlockCallback.EVENT.register((p, w, h, hit) -> isStunned(p) ? ActionResult.FAIL : ActionResult.PASS);
        UseEntityCallback.EVENT.register((p, w, h, e, hit) -> isStunned(p) ? ActionResult.FAIL : ActionResult.PASS);
        UseItemCallback.EVENT.register((p, w, h) -> isStunned(p)
                ? TypedActionResult.fail(p.getStackInHand(h)) : TypedActionResult.pass(p.getStackInHand(h)));
    }

    public static boolean isStunned(Entity e) {
        Stun s = STUNNED.get(e);
        return s != null && e.getWorld().getTime() - s.last <= 1;
    }

    // ------------------------------------------------------------------ firing and letting go

    static void toggle(ServerPlayerEntity p, Hand hand) {
        ServerWorld world = p.getServerWorld();
        if (isStunned(p)) return;   // a stunned holder can't let go: it runs until the battery's flat
        if (LINES.remove(p.getUuid()) != null) {
            world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.PLAYERS, 0.6F, 0.7F);
            return;
        }
        ItemStack stack = p.getStackInHand(hand);
        if (stack.getDamage() >= stack.getMaxDamage()) {
            p.sendMessage(Text.literal("The taser's battery is flat. Craft it with redstone dust to recharge it.").formatted(Formatting.GRAY), true);
            world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 0.6F, 1.6F);
            return;
        }
        Vec3d eye = p.getEyePos();
        Vec3d look = p.getRotationVec(1.0F);
        Vec3d far = eye.add(look.multiply(REACH));
        HitResult blockHit = p.raycast(REACH, 1.0F, false);
        double blockDist = blockHit.getType() == HitResult.Type.MISS ? REACH * REACH : blockHit.getPos().squaredDistanceTo(eye);
        EntityHitResult entityHit = ProjectileUtil.raycast(p, eye, far, p.getBoundingBox().stretch(look.multiply(REACH)).expand(1.0),
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e != p, blockDist);
        world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_FISHING_BOBBER_THROW, SoundCategory.PLAYERS, 0.6F, 1.6F);
        Line line = null;
        if (entityHit != null) {
            line = new Line(world, entityHit.getEntity(), null, hand);
            if (entityHit.getEntity() instanceof CreeperEntity creeper && !(entityHit.getEntity() instanceof AbstractSkeletonEntity)) charge(creeper);
        } else if (blockHit instanceof BlockHitResult bh && blockHit.getType() == HitResult.Type.BLOCK
                && world.getBlockState(bh.getBlockPos()).isIn(CONDUCTS)) {
            line = new Line(world, null, bh.getBlockPos().toImmutable(), hand);
        }
        Vec3d end = line != null ? line.end() : (blockHit.getType() == HitResult.Type.MISS ? far : blockHit.getPos());
        sparks(world, handPos(p, hand), end, 10);
        if (line == null) {
            p.sendMessage(Text.literal("The darts didn't catch on anything.").formatted(Formatting.GRAY), true);
            return;
        }
        LINES.put(p.getUuid(), line);
        world.playSound(null, end.x, end.y, end.z, SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.PLAYERS, 0.8F, 1.4F);
    }

    /** A creeper hit by the darts becomes charged, like a lightning strike. */
    private static void charge(CreeperEntity creeper) {
        NbtCompound n = new NbtCompound();
        creeper.writeNbt(n);
        if (n.getBoolean("powered")) return;
        n.putBoolean("powered", true);
        creeper.readNbt(n);
    }

    private static Vec3d handPos(PlayerEntity p, Hand hand) {
        Vec3d look = p.getRotationVec(1.0F);
        Vec3d side = new Vec3d(-look.z, 0, look.x).normalize().multiply(hand == Hand.MAIN_HAND ? 0.35 : -0.35);
        return p.getEyePos().add(look.multiply(0.5)).add(side).add(0, -0.35, 0);
    }

    // ------------------------------------------------------------------ every tick

    private static void tick(MinecraftServer server) {
        if (LINES.isEmpty() && STUNNED.isEmpty() && LIGHTS.isEmpty()) return;
        Set<Entity> stunnedNow = new LinkedHashSet<>();
        Map<BlockPos, ServerWorld> copperNow = new HashMap<>();
        for (Iterator<Map.Entry<UUID, Line>> it = LINES.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Line> en = it.next();
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(en.getKey());
            Line line = en.getValue();
            String broke = broken(p, line);
            if (broke != null) {
                it.remove();
                if (p != null) {
                    p.sendMessage(Text.literal(broke).formatted(Formatting.GRAY), true);
                    line.world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_LEASH_KNOT_BREAK, SoundCategory.PLAYERS, 0.7F, 1.5F);
                }
                continue;
            }
            ItemStack stack = p.getStackInHand(line.hand);
            stack.setDamage(Math.min(stack.getMaxDamage(), stack.getDamage() + 1));   // the battery drains while the line is on
            long now = line.world.getTime();
            if (now % 2 == 0) sparks(line.world, handPos(p, line.hand), line.end(), 5);
            if (now % 20 == 0) {
                int left = (stack.getMaxDamage() - stack.getDamage()) / 20;
                p.sendMessage(Text.literal("Taser battery: " + left / 60 + ":" + String.format("%02d", left % 60)).formatted(Formatting.YELLOW), true);
            }
            // Water shorts it out: nothing happens while the holder or the target is wet.
            if (p.isTouchingWater() || (line.target != null && line.target.isTouchingWater())) continue;
            flow(line, stunnedNow, copperNow);
        }
        long now = server.getOverworld().getTime();
        for (Entity e : stunnedNow) stun(e);
        // Release whatever wasn't stunned this tick.
        for (Iterator<Map.Entry<Entity, Stun>> it = STUNNED.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Entity, Stun> en = it.next();
            if (!stunnedNow.contains(en.getKey())) {
                release(en.getKey());
                it.remove();
            }
        }
        glow(stunnedNow, copperNow);
    }

    private static String broken(ServerPlayerEntity p, Line line) {
        if (p == null || !p.isAlive() || p.getServerWorld() != line.world) return "The taser line broke.";
        ItemStack stack = p.getStackInHand(line.hand);
        if (!stack.isOf(TASER)) return "You let go of the taser: the line broke.";
        if (stack.getDamage() >= stack.getMaxDamage()) return "The taser's battery ran flat.";
        if (line.target != null) {
            if (!line.target.isAlive() || line.target.isRemoved() || line.target.getWorld() != line.world) return "The taser line broke.";
            if (line.target.squaredDistanceTo(p) > SNAP * SNAP) return "Too far: the taser line snapped.";
        } else {
            if (!line.world.getBlockState(line.block).isIn(CONDUCTS)) return "The taser line broke.";
            if (Vec3d.ofCenter(line.block).squaredDistanceTo(p.getPos()) > SNAP * SNAP) return "Too far: the taser line snapped.";
        }
        return null;
    }

    private static boolean stunnable(Entity e) {
        if (!(e instanceof LivingEntity) || !e.isAlive() || e instanceof AbstractSkeletonEntity) return false;
        if (e instanceof PlayerEntity pl && (pl.isCreative() || pl.isSpectator())) return false;
        if (e.isTouchingWater()) return false;
        return !IMMUNE.test(e);
    }

    /** Electricity spreads out from the line: through touching things and joined copper. */
    private static void flow(Line line, Set<Entity> stunned, Map<BlockPos, ServerWorld> copper) {
        ServerWorld w = line.world;
        ArrayDeque<Entity> things = new ArrayDeque<>();
        ArrayDeque<BlockPos> blocks = new ArrayDeque<>();
        if (line.target != null) {
            if (stunnable(line.target) && stunned.add(line.target)) things.add(line.target);
        } else {
            energize(w, line.block, copper, blocks);
        }
        int guard = 0;
        while ((!things.isEmpty() || !blocks.isEmpty()) && guard++ < 4096) {
            if (!things.isEmpty()) {
                Entity e = things.poll();
                Box box = e.getBoundingBox().expand(0.05);
                // copper it touches
                for (BlockPos pos : BlockPos.iterate(BlockPos.ofFloored(box.minX, box.minY, box.minZ), BlockPos.ofFloored(box.maxX, box.maxY, box.maxZ))) {
                    if (!copper.containsKey(pos) && w.getBlockState(pos).isIn(CONDUCTS)) energize(w, pos.toImmutable(), copper, blocks);
                }
                // things it touches
                if (stunned.size() < MAX_STUNNED) {
                    for (Entity o : w.getOtherEntities(e, box, Taser::stunnable)) {
                        if (stunned.size() >= MAX_STUNNED) break;
                        if (stunned.add(o)) things.add(o);
                    }
                }
            } else {
                BlockPos pos = blocks.poll();
                if (stunned.size() >= MAX_STUNNED) continue;
                for (Entity o : w.getOtherEntities(null, new Box(pos).expand(0.05), Taser::stunnable)) {
                    if (stunned.size() >= MAX_STUNNED) break;
                    if (stunned.add(o)) things.add(o);
                }
            }
        }
    }

    /** Every copper block joined face to face with this one carries the current. */
    private static void energize(ServerWorld w, BlockPos start, Map<BlockPos, ServerWorld> copper, ArrayDeque<BlockPos> out) {
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        q.add(start);
        copper.put(start, w);
        out.add(start);
        while (!q.isEmpty() && copper.size() < MAX_COPPER) {
            BlockPos p = q.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = p.offset(d);
                if (copper.containsKey(n)) continue;
                BlockState st = w.getBlockState(n);
                if (st.isIn(CONDUCTS)) {
                    copper.put(n, w);
                    q.add(n);
                    out.add(n);
                }
            }
        }
    }

    // ------------------------------------------------------------------ being stunned

    private static void stun(Entity e) {
        ServerWorld w = (ServerWorld) e.getWorld();
        long now = w.getTime();
        Stun s = STUNNED.get(e);
        if (s == null) {
            s = new Stun(e.getPos(), now);
            STUNNED.put(e, s);
            if (e instanceof LivingEntity le) {
                EntityAttributeInstance speed = le.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
                if (speed != null && speed.getModifier(SLOW_ID) == null) speed.addTemporaryModifier(STILL);
            }
        }
        s.last = now;
        // Gravity still works: a stunned thing falls. Only walking and jumping are stopped.
        if (e.getY() < s.anchor.y) s.anchor = new Vec3d(s.anchor.x, e.getY(), s.anchor.z);
        if (e instanceof CreeperEntity c) c.setFuseSpeed(-1);
        if (e instanceof ServerPlayerEntity p) {
            // Held in place: no walking and no jumping (any step or hop is put straight back), but free to fall.
            double dx = p.getX() - s.anchor.x, dz = p.getZ() - s.anchor.z;
            if (dx * dx + dz * dz > 0.0025 || p.getY() > s.anchor.y + 0.05) {
                p.networkHandler.requestTeleport(s.anchor.x, Math.min(p.getY(), s.anchor.y), s.anchor.z, p.getYaw(), p.getPitch());
            }
            if (now % 10 == 0) p.sendMessage(Text.literal("Stunned!").formatted(Formatting.YELLOW), true);
        } else {
            // Shake in place.
            double j = 0.045;
            e.setVelocity(0, Math.min(0, e.getVelocity().y), 0);   // no walking or jumping, but gravity still pulls
            e.refreshPositionAfterTeleport(s.anchor.x + (w.random.nextDouble() - 0.5) * j * 2, e.getY(), s.anchor.z + (w.random.nextDouble() - 0.5) * j * 2);
            if (e instanceof MobEntity m) m.getNavigation().stop();
        }
        if (now % 3 == 0) {
            w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY() + e.getHeight() * 0.5, e.getZ(), 2,
                    e.getWidth() * 0.4, e.getHeight() * 0.35, e.getWidth() * 0.4, 0.05);
        }
        if (s.sound-- <= 0) {
            s.sound = 10;   // the buzz is half a second long: keep it going
            w.playSound(null, e.getX(), e.getY(), e.getZ(), BUZZ, SoundCategory.NEUTRAL, 0.6F, 0.9F + w.random.nextFloat() * 0.2F);
        }
    }

    private static void release(Entity e) {
        if (e instanceof LivingEntity le) {
            EntityAttributeInstance speed = le.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
            if (speed != null) speed.removeModifier(SLOW_ID);
        }
        if (e instanceof ServerPlayerEntity p) p.sendMessage(Text.empty(), true);
    }

    private static void sparks(ServerWorld w, Vec3d from, Vec3d to, int n) {
        for (int i = 1; i <= n; i++) {
            Vec3d p = from.lerp(to, i / (double) (n + 1));
            w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    // ------------------------------------------------------------------ the glow (a redstone torch's worth)

    private static void glow(Set<Entity> stunned, Map<BlockPos, ServerWorld> copper) {
        Map<BlockPos, ServerWorld> want = new HashMap<>();
        for (Entity e : stunned) {
            ServerWorld w = (ServerWorld) e.getWorld();
            BlockPos eyes = BlockPos.ofFloored(e.getEyePos());
            BlockPos at = free(w, eyes) ? eyes : free(w, e.getBlockPos()) ? e.getBlockPos() : null;
            if (at != null) want.put(at, w);
        }
        int n = 0;
        for (Map.Entry<BlockPos, ServerWorld> c : copper.entrySet()) {
            if (n >= MAX_COPPER_LIGHTS) break;
            for (Direction d : new Direction[]{Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN}) {
                BlockPos at = c.getKey().offset(d);
                if (free(c.getValue(), at)) {
                    want.put(at, c.getValue());
                    n++;
                    break;
                }
            }
        }
        for (Iterator<Map.Entry<BlockPos, ServerWorld>> it = LIGHTS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<BlockPos, ServerWorld> e = it.next();
            if (want.get(e.getKey()) != e.getValue()) {
                unlight(e.getValue(), e.getKey());
                it.remove();
            }
        }
        for (Map.Entry<BlockPos, ServerWorld> e : want.entrySet()) {
            if (LIGHTS.containsKey(e.getKey())) continue;
            e.getValue().setBlockState(e.getKey(), Blocks.LIGHT.getDefaultState().with(LightBlock.LEVEL_15, GLOW), Block.NOTIFY_ALL);
            LIGHTS.put(e.getKey().toImmutable(), e.getValue());
        }
    }

    private static boolean free(ServerWorld w, BlockPos pos) {
        BlockState st = w.getBlockState(pos);
        return st.isAir() || (LIGHTS.containsKey(pos) && st.isOf(Blocks.LIGHT));
    }

    private static void unlight(ServerWorld w, BlockPos pos) {
        BlockState st = w.getBlockState(pos);
        if (st.isOf(Blocks.LIGHT) && st.get(LightBlock.LEVEL_15) == GLOW) w.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
    }
}
