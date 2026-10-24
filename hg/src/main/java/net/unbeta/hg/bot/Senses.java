package net.unbeta.hg.bot;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * A bot's eyes. Each look (every few ticks) it sees living things, items and projectiles in its
 * view cone with a clear line of sight, and casts glance rays across the cone to notice blocks
 * (ore inside stone stays secret until something exposes it). It takes in at most a few new things
 * per look, and acts on each only after a short human reaction delay. Everything noticed goes
 * into the notebook.
 */
public final class Senses {

    private final Brain brain;
    private final Random random = new Random();

    /** Things in view right now, with the tick from which the bot may act on each. */
    private final Map<Integer, Long> usableFrom = new HashMap<>();
    private final List<Entity> inView = new ArrayList<>();

    Senses(Brain brain) {
        this.brain = brain;
    }

    /** Things in view that the bot has had time to react to. */
    public List<Entity> visible() {
        long now = brain.now();
        List<Entity> out = new ArrayList<>();
        for (Entity e : inView) {
            Long from = usableFrom.get(e.getId());
            if (from != null && from <= now && !e.isRemoved()) out.add(e);
        }
        return out;
    }

    public <T extends Entity> List<T> visible(Class<T> type) {
        List<T> out = new ArrayList<>();
        for (Entity e : visible()) if (type.isInstance(e)) out.add(type.cast(e));
        return out;
    }

    public boolean sees(Entity e) {
        Long from = usableFrom.get(e.getId());
        return from != null && from <= brain.now() && inView.contains(e);
    }

    // ------------------------------------------------------------------ looking

    void look(boolean busy) {
        BotConfig cfg = BotConfig.get();
        net.unbeta.hg.DummyPlayer bot = brain.bot();
        ServerWorld world = brain.world();
        long now = brain.now();
        double cone = busy ? cfg.busyConeDegrees : cfg.sightConeDegrees;
        Vec3d eye = bot.getEyePos();
        Vec3d facing = bot.getRotationVec(1F);
        double cosHalf = Math.cos(Math.toRadians(cone / 2));

        // Living things, items and projectiles in the cone, in sight.
        double range = cfg.sightRange;
        Box box = bot.getBoundingBox().expand(range);
        List<Entity> candidates = world.getOtherEntities(bot, box, e -> e instanceof LivingEntity
                || e instanceof ItemEntity || e instanceof ProjectileEntity || e instanceof net.minecraft.entity.ExperienceOrbEntity);
        List<Entity> inSight = new ArrayList<>();
        for (Entity e : candidates) {
            if (e instanceof LivingEntity le && (le.isDead() || le.isInvisible())) continue;
            if (e instanceof PlayerEntity p && p.isSpectator()) continue;
            Vec3d to = e.getEyePos().subtract(eye);
            double dist = to.length();
            double limit = range;
            boolean dark = world.getLightLevel(e.getBlockPos()) <= 4 && !e.isOnFire();
            if (dark) limit = cfg.darkSightRange;
            if (!(e instanceof LivingEntity)) limit = Math.min(limit, 16);
            if (dist > limit) continue;
            if (dist > 1.5 && to.normalize().dotProduct(facing) < cosHalf) continue;
            if (!bot.canSee(e)) continue;
            inSight.add(e);
        }
        // Attention: what it already had in view stays; at most N new things, most alarming first.
        inSight.sort(Comparator.comparingDouble(e -> alarm(e) * 1000 + e.squaredDistanceTo(bot)));
        int fresh = 0;
        List<Entity> kept = new ArrayList<>();
        for (Entity e : inSight) {
            if (usableFrom.containsKey(e.getId())) {
                kept.add(e);
            } else if (fresh < cfg.newThingsPerLook) {
                fresh++;
                kept.add(e);
                usableFrom.put(e.getId(), now + brain.reactionDelay());
            }
        }
        usableFrom.keySet().removeIf(id -> kept.stream().noneMatch(e -> e.getId() == id));
        inView.clear();
        inView.addAll(kept);
        for (Entity e : kept) if (e instanceof PlayerEntity p) notice(p, now);

        // Glance rays: blocks.
        int rays = Math.max(1, (int) Math.round(cfg.glanceRaysPerSecond * cfg.thinkTicks / 20.0));
        List<Glimpse> glimpses = new ArrayList<>();
        boolean carriesLight = net.unbeta.content.light.HeldLight.isLit(bot.getMainHandStack())
                || net.unbeta.content.light.HeldLight.isLit(bot.getOffHandStack());
        for (int i = 0; i < rays; i++) {
            float yaw = bot.getYaw() + (float) ((random.nextDouble() - 0.5) * cone);
            float pitch = MathHelper.clamp(bot.getPitch() + (float) ((random.nextDouble() - 0.5) * 100), -80F, 80F);
            Vec3d dir = Vec3d.fromPolar(pitch, yaw);
            Vec3d end = eye.add(dir.multiply(cfg.glanceRange));
            BlockHitResult hit = world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.ANY, bot));
            if (hit.getType() != HitResult.Type.BLOCK) continue;
            BlockPos pos = hit.getBlockPos();
            BlockState state = world.getBlockState(pos);
            // A mark that isn't what it was any more is crossed out.
            for (Notebook.Kind k : Notebook.Kind.values()) {
                if (brain.notebook.has(k, pos) && classify(world, pos, state) != k && k != Notebook.Kind.DANGER
                        && k != Notebook.Kind.DEATH) brain.notebook.forget(k, pos);
            }
            Notebook.Kind kind = classify(world, pos, state);
            BlockPos air = pos.offset(hit.getSide());
            // In the dark it can't make things out past a few blocks (lava and flames light themselves).
            boolean seen = carriesLight || world.getLightLevel(air) > 2
                    || pos.getSquaredDistance(bot.getBlockPos()) <= cfg.darkGlanceRange * cfg.darkGlanceRange;
            if (kind != null && (seen || kind == Notebook.Kind.LAVA || kind == Notebook.Kind.FLAME)) {
                glimpses.add(new Glimpse(kind, pos.toImmutable(), describe(state)));
            }
            // A way down: open space it can see into, well below the surface, that sky never reaches.
            if (world.isAir(air) && world.getLightLevel(net.minecraft.world.LightType.SKY, air) == 0
                    && air.getY() < world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, air.getX(), air.getZ()) - 4
                    && brain.notebook.nearest(Notebook.Kind.CAVE, air, 6) == null) {
                glimpses.add(new Glimpse(Notebook.Kind.CAVE, air.toImmutable(), "cave"));
            }
        }
        glimpses.sort(Comparator.comparingDouble(g -> g.pos.getSquaredDistance(bot.getBlockPos())));
        int newMarks = 0;
        for (Glimpse g : glimpses) {
            if (brain.notebook.has(g.kind, g.pos)) {
                brain.notebook.note(g.kind, g.pos, now, g.what, bot.getBlockPos());
            } else if (newMarks < cfg.newThingsPerLook) {
                newMarks++;
                brain.notebook.note(g.kind, g.pos, now, g.what, bot.getBlockPos());
            }
        }
    }

    private record Glimpse(Notebook.Kind kind, BlockPos pos, String what) {}

    /** Lower is more alarming. */
    private static int alarm(Entity e) {
        if (e instanceof Monster) return 0;
        if (e instanceof PlayerEntity) return 1;
        if (e instanceof ItemEntity || e instanceof ProjectileEntity) return 2;
        return 3;
    }

    /** What a player is visibly doing, kept in the notebook. */
    private void notice(PlayerEntity p, long now) {
        Notebook.Sighting s = brain.notebook.sighting(p.getUuid());
        Vec3d before = s.pos;
        s.name = p.getName().getString();
        s.heading = before == null ? Vec3d.ZERO : p.getPos().subtract(before);
        s.pos = p.getPos();
        s.holding = p.getMainHandStack().isEmpty() ? "nothing" : p.getMainHandStack().getName().getString();
        s.holdingItem = p.getMainHandStack().isEmpty() ? null : p.getMainHandStack().getItem();
        s.armed = Body.weaponDamage(p.getMainHandStack()) > 0 || p.getMainHandStack().getItem() instanceof net.minecraft.item.BowItem;
        if (s.holdingItem != null && now - s.seen > 100) brain.notebook.handsSeen.merge(s.holdingItem, 1, Integer::sum);
        boolean close = p.squaredDistanceTo(brain.bot()) < 24 * 24;
        if (!close) s.closeSince = -1;
        else if (s.closeSince < 0 || now - s.seen > 200) s.closeSince = now;
        s.seen = now;
        s.showing = flavour(p, s);
    }

    /** The flavour anyone can read off a player: running away, swinging at someone, at blocks, standing at a table. */
    private Reaction.Tag flavour(PlayerEntity p, Notebook.Sighting s) {
        ServerWorld world = brain.world();
        Vec3d away = p.getPos().subtract(brain.bot().getPos());
        if (p.isSprinting() && s.heading.horizontalLengthSquared() > 0.1 && s.heading.dotProduct(away) > 0) return Reaction.Tag.FEARFUL;
        if (p.handSwinging) {
            for (PlayerEntity other : world.getPlayers()) {
                if (other != p && other.squaredDistanceTo(p) < 16 && p.canSee(other)) return Reaction.Tag.AGGRESSIVE;
            }
            return Reaction.Tag.GATHERING;
        }
        HitResult look = p.raycast(4.5, 0F, false);
        if (look instanceof BlockHitResult bh && look.getType() == HitResult.Type.BLOCK) {
            BlockState at = world.getBlockState(bh.getBlockPos());
            if (at.isOf(Blocks.CRAFTING_TABLE) || at.isOf(Blocks.FURNACE)) return Reaction.Tag.CRAFTING;
        }
        return null;
    }

    // ------------------------------------------------------------------ what a block is to a bot

    static Notebook.Kind classify(ServerWorld world, BlockPos pos, BlockState state) {
        if (state.getFluidState().isStill()) {
            if (state.getFluidState().isIn(FluidTags.LAVA)) return Notebook.Kind.LAVA;
            if (state.getFluidState().isIn(FluidTags.WATER)) return Notebook.Kind.WATER;
        }
        if (state.isIn(BlockTags.LOGS)) return Notebook.Kind.LOG;
        if (state.isIn(BlockTags.RAILS)) return Notebook.Kind.RAIL;
        if (isOre(state)) return Notebook.Kind.ORE;
        if (state.isOf(Blocks.COBBLESTONE) || state.isOf(Blocks.MOSSY_COBBLESTONE)) return Notebook.Kind.COBBLE;
        if (state.isOf(Blocks.OAK_LEAVES)) return Notebook.Kind.LEAVES;
        if (state.isOf(Blocks.GRAVEL)) return Notebook.Kind.GRAVEL;
        if (state.isOf(Blocks.PUMPKIN)) return Notebook.Kind.PUMPKIN;
        if (state.isOf(Blocks.CRAFTING_TABLE)) return Notebook.Kind.TABLE;
        if (isFlame(state)) return Notebook.Kind.FLAME;
        if (state.isOf(Blocks.FURNACE)) return Notebook.Kind.FURNACE;
        if (state.isOf(Blocks.CHEST) || state.isOf(Blocks.TRAPPED_CHEST) || state.isOf(Blocks.BARREL)) return Notebook.Kind.CHEST;
        Identifier id = Registries.BLOCK.getId(state.getBlock());
        if (id.getNamespace().equals("unbeta-content")) {
            if (id.getPath().equals("clambox")) return Notebook.Kind.CLAMBOX;
            if (id.getPath().equals("obsidian_door")) return Notebook.Kind.DOOR;
        }
        if (state.isIn(BlockTags.DOORS) || state.isIn(BlockTags.BEDS) || state.isOf(Blocks.LADDER)) return Notebook.Kind.PLACED;
        return null;
    }

    static boolean isOre(BlockState s) {
        return s.isIn(BlockTags.COAL_ORES) || s.isIn(BlockTags.IRON_ORES) || s.isIn(BlockTags.GOLD_ORES)
                || s.isIn(BlockTags.COPPER_ORES) || s.isIn(BlockTags.DIAMOND_ORES) || s.isIn(BlockTags.REDSTONE_ORES)
                || s.isIn(BlockTags.LAPIS_ORES) || s.isIn(BlockTags.EMERALD_ORES);
    }

    /** Something an unlit torch can be lit from: fire, a lit furnace, a lit torch or Jack o'Lantern, burning Burnt blocks. */
    static boolean isFlame(BlockState state) {
        if (state.isIn(BlockTags.FIRE)) return true;
        Identifier id = Registries.BLOCK.getId(state.getBlock());
        if (id.getNamespace().equals("burnt")) {
            String path = id.getPath();
            return path.startsWith("blazing_") || path.startsWith("smoldering_") || path.startsWith("ember_")
                    || path.contains("fire") || path.contains("flame");
        }
        boolean lightable = state.isOf(Blocks.FURNACE) || id.getPath().contains("torch") || id.getPath().contains("jack_o_lantern");
        if (!lightable) return false;
        for (Property<?> p : state.getProperties()) {
            if (p.getName().equals("lit") && p instanceof BooleanProperty lit) return state.get(lit);
        }
        return false;
    }

    private static String describe(BlockState state) {
        if (state.getFluidState().isStill()) {
            if (state.getFluidState().isIn(FluidTags.LAVA)) return "lava";
            if (state.getFluidState().isIn(FluidTags.WATER)) return "water";
        }
        return state.getBlock().getName().getString();
    }
}
