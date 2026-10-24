package net.unbeta.hg.bot;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.unbeta.hg.DummyPlayer;
import net.unbeta.hg.UnbetaHG;

/**
 * A bot's body: the moves its orders (and, from Phase 2, its Reactions) are made of. Walking a
 * path (worked out by the zombie pathfinder), turning its head at human speed, breaking blocks
 * in the real break time, placing blocks, picking the right tool. One order runs at a time.
 */
public final class Body {

    public final DummyPlayer bot;
    public final Traits traits;
    /** Its brain (Phase 2): runs whenever no order from /hg bot is running. */
    public final Brain brain;
    private Action action;
    /** While the brain drives, orders' chatter ("made a wooden pickaxe") stays out of chat. */
    boolean quiet;
    /** The last thing it would have said while quiet (shown by /hg bot why). */
    String lastSaid = "";

    public Body(DummyPlayer bot, Traits traits) {
        this.bot = bot;
        this.traits = traits;
        this.brain = new Brain(this);
    }

    /** One thing a bot is doing. tick() returns true when it has finished (or given up). */
    public interface Action {
        boolean tick(Body body);

        String describe();

        default void stopped(Body body) {}
    }

    // ------------------------------------------------------------------ running orders

    public void tick() {
        if (action == null) {
            brain.tick();
            return;
        }
        boolean done;
        try {
            done = action.tick(this);
        } catch (Exception e) {
            UnbetaHG.LOG.error("Bot {} failed while {}", bot.getName().getString(), action.describe(), e);
            say("got confused while " + action.describe() + " (see the server log)");
            done = true;
        }
        if (done) {
            action = null;
            stopMoving();
            stopBreaking();
        }
    }

    /** An order from /hg bot: the brain stands aside until it's done. */
    public void start(Action next) {
        stop();
        brain.standAside();
        action = next;
    }

    public boolean hasOrder() {
        return action != null;
    }

    public void stop() {
        if (action != null) action.stopped(this);
        action = null;
        stopMoving();
        stopBreaking();
    }

    public String doing() {
        return action == null ? "nothing" : action.describe();
    }

    public ServerWorld world() {
        return bot.getServerWorld();
    }

    /** Says something in chat, in grey, for testing. */
    public void say(String text) {
        if (quiet) {
            lastSaid = text;
            return;
        }
        bot.getServer().getPlayerManager().broadcast(
                Text.literal("[" + bot.getName().getString() + "] " + text).formatted(Formatting.GRAY), false);
    }

    // ------------------------------------------------------------------ looking

    /** Turns its head toward a point, no faster than the tuning file allows. True once facing it. */
    public boolean lookAt(Vec3d point) {
        Vec3d eye = bot.getEyePos();
        double dx = point.x - eye.x, dy = point.y - eye.y, dz = point.z - eye.z;
        double flat = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (MathHelper.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
        float pitch = (float) -(MathHelper.atan2(dy, flat) * (180.0 / Math.PI));
        float step = BotConfig.get().turn();
        float dYaw = MathHelper.clamp(MathHelper.wrapDegrees(yaw - bot.getYaw()), -step, step);
        float dPitch = MathHelper.clamp(pitch - bot.getPitch(), -step, step);
        float newYaw = bot.getYaw() + dYaw;
        bot.setYaw(newYaw);
        bot.setHeadYaw(newYaw);
        bot.setBodyYaw(newYaw);
        bot.setPitch(MathHelper.clamp(bot.getPitch() + dPitch, -90F, 90F));
        return Math.abs(MathHelper.wrapDegrees(yaw - newYaw)) < 10F && Math.abs(pitch - bot.getPitch()) < 10F;
    }

    // ------------------------------------------------------------------ walking

    public enum Walk { WALKING, ARRIVED, FAILED }

    private ZombieEntity guide;
    private Path path;
    private int node;
    private BlockPos goal;
    private double within;
    private Vec3d lastProgressAt;
    private int noProgress;
    private int replans;

    /**
     * Walks toward a spot until within the given distance of its centre. Call every tick; it
     * answers WALKING, ARRIVED or FAILED (no way there, or stuck after 5 tries).
     */
    public Walk walkTo(BlockPos target, double withinBlocks) {
        Vec3d centre = Vec3d.ofBottomCenter(target);
        if (bot.getPos().distanceTo(centre) <= withinBlocks) {
            stopMoving();
            return Walk.ARRIVED;
        }
        if (goal == null || !goal.equals(target)) {
            goal = target.toImmutable();
            within = withinBlocks;
            replans = 0;
            noProgress = 0;
            lastProgressAt = bot.getPos();
            if (!plan()) return fail();
        }
        if (path == null || node >= path.getLength()) {
            // End of a leg (or a partial path): plan the next one from here.
            if (!plan()) return fail();
        }
        // Stuck?
        if (bot.getPos().squaredDistanceTo(lastProgressAt) > 1.0) {
            lastProgressAt = bot.getPos();
            noProgress = 0;
        } else if (++noProgress > BotConfig.get().stuckTicks) {
            noProgress = 0;
            if (++replans > 5 || !plan()) return fail();
        }
        follow(centre);
        return Walk.WALKING;
    }

    private Walk fail() {
        stopMoving();
        goal = null;
        return Walk.FAILED;
    }

    /** Plans a path to the goal, or to a waypoint one leg toward it when it is far. */
    private boolean plan() {
        ServerWorld world = world();
        BlockPos target = goal;
        int leg = BotConfig.get().pathLegBlocks;
        double dx = goal.getX() + 0.5 - bot.getX(), dz = goal.getZ() + 0.5 - bot.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        if (flat > leg + 8) {
            int x = MathHelper.floor(bot.getX() + dx / flat * leg), z = MathHelper.floor(bot.getZ() + dz / flat * leg);
            target = new BlockPos(x, world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        }
        if (guide == null || guide.getWorld() != world) {
            guide = EntityType.ZOMBIE.create(world);
            if (guide == null) return false;
            guide.getAttributeInstance(EntityAttributes.GENERIC_FOLLOW_RANGE).setBaseValue(leg + 32);
            guide.getNavigation().setCanSwim(true);
        }
        guide.refreshPositionAndAngles(bot.getX(), bot.getY(), bot.getZ(), bot.getYaw(), 0F);
        guide.setOnGround(true);
        Path found = guide.getNavigation().findPathTo(target, (int) Math.max(0, Math.floor(within) - 1));
        if (found == null || found.getLength() == 0) {
            path = null;
            return false;
        }
        path = found;
        node = 0;
        return true;
    }

    private void follow(Vec3d finalCentre) {
        // Skip the nodes we're already on or past.
        while (node < path.getLength()) {
            BlockPos n = path.getNodePos(node);
            double ndx = n.getX() + 0.5 - bot.getX(), ndz = n.getZ() + 0.5 - bot.getZ();
            if (ndx * ndx + ndz * ndz < 0.35 && Math.abs(bot.getY() - n.getY()) < 1.2) node++;
            else break;
        }
        if (node >= path.getLength()) {
            halt();
            return;
        }
        BlockPos n = path.getNodePos(node);
        Vec3d aim = new Vec3d(n.getX() + 0.5, n.getY() + bot.getStandingEyeHeight(), n.getZ() + 0.5);
        lookAt(aim);
        double ndx = aim.x - bot.getX(), ndz = aim.z - bot.getZ();
        float want = (float) (MathHelper.atan2(ndz, ndx) * (180.0 / Math.PI)) - 90.0F;
        float off = Math.abs(MathHelper.wrapDegrees(want - bot.getYaw()));
        bot.forwardSpeed = off > 60F ? 0.2F : 1.0F;
        bot.sidewaysSpeed = 0F;
        boolean wet = bot.isTouchingWater() || bot.isInLava();
        bot.setJumping(n.getY() > bot.getBlockY() || bot.horizontalCollision || wet);
        double left = bot.getPos().distanceTo(finalCentre);
        bot.setSprinting(!noSprint && left > BotConfig.get().sprintOver && !wet && bot.getHungerManager().getFoodLevel() > 6 && off < 30F);
    }

    public void stopMoving() {
        halt();
        path = null;
        goal = null;
    }

    /** Stops its legs, keeping the walk it is on. */
    private void halt() {
        bot.forwardSpeed = 0F;
        bot.sidewaysSpeed = 0F;
        bot.setJumping(false);
        bot.setSprinting(false);
    }

    // ------------------------------------------------------------------ hands and tools

    /** Puts the item in the given inventory slot into its hand. */
    public void holdSlot(int slot) {
        PlayerInventory inv = bot.getInventory();
        if (slot < 0) return;
        if (slot < 9) {
            inv.selectedSlot = slot;
            return;
        }
        ItemStack there = inv.getStack(slot);
        inv.setStack(slot, inv.getStack(inv.selectedSlot));
        inv.setStack(inv.selectedSlot, there);
    }

    /** Holds an item of this kind, if it has one. */
    public boolean hold(Item item) {
        PlayerInventory inv = bot.getInventory();
        for (int i = 0; i < 36; i++) {
            if (inv.getStack(i).isOf(item)) {
                holdSlot(i);
                return true;
            }
        }
        return false;
    }

    /** Holds the fastest tool it has for a block (one that gets the drop, if the block needs one). */
    public void holdToolFor(BlockState state) {
        PlayerInventory inv = bot.getInventory();
        int best = -1;
        float bestSpeed = 1F;
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty()) continue;
            if (state.isToolRequired() && !s.isSuitableFor(state)) continue;
            float speed = s.getMiningSpeedMultiplier(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                best = i;
            }
        }
        holdSlot(best);
    }

    // ------------------------------------------------------------------ breaking

    public enum Dig { DIGGING, DONE, FAILED }

    private BlockPos breaking;
    private float breakProgress;
    private int breakStage = -1;

    /** Breaks a block in reach, in its real break time with the best tool it has. */
    public Dig dig(BlockPos pos) {
        ServerWorld world = world();
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) {
            stopBreaking();
            return Dig.DONE;
        }
        if (state.getHardness(world, pos) < 0 || !canHarvest(state)) {
            // Unbreakable, or it lacks the tool (Unbeta stone needs an iron pick): don't punch at it.
            stopBreaking();
            return Dig.FAILED;
        }
        if (!pos.equals(breaking)) {
            stopBreaking();
            breaking = pos.toImmutable();
            breakProgress = 0F;
            holdToolFor(state);
        }
        stopMoving();
        lookAt(Vec3d.ofCenter(pos));
        if (bot.age % 5 == 0) bot.swingHand(Hand.MAIN_HAND);
        breakProgress += state.calcBlockBreakingDelta(bot, world, pos);
        int stage = Math.min(9, (int) (breakProgress * 10F));
        if (stage != breakStage) {
            world.setBlockBreakingInfo(bot.getId(), pos, stage);
            breakStage = stage;
        }
        if (breakProgress >= 1F) {
            boolean ok = bot.interactionManager.tryBreakBlock(pos);
            stopBreaking();
            return ok ? Dig.DONE : Dig.FAILED;
        }
        return Dig.DIGGING;
    }

    /** Has it something that breaks this block properly (or does it need no tool)? */
    public boolean canHarvest(BlockState state) {
        if (!state.isToolRequired()) return true;
        PlayerInventory inv = bot.getInventory();
        for (int i = 0; i < 36; i++) if (inv.getStack(i).isSuitableFor(state)) return true;
        return false;
    }

    /** Tortoises never sprint while gearing up. */
    public boolean noSprint;

    public void stopBreaking() {
        if (breaking != null) world().setBlockBreakingInfo(bot.getId(), breaking, -1);
        breaking = null;
        breakStage = -1;
        breakProgress = 0F;
    }

    // ------------------------------------------------------------------ placing

    /**
     * Places a block item on the ground right beside it (a crafting table, a furnace). Returns
     * where it went, or null if there was nowhere to put it.
     */
    public BlockPos placeBeside(Item item, Block block) {
        if (!hold(item)) return null;
        ServerWorld world = world();
        BlockPos feet = bot.getBlockPos();
        // Every spot within reach that has solid ground under it and room in it (not where it stands), nearest first.
        java.util.List<BlockPos> spots = new java.util.ArrayList<>();
        for (BlockPos p : BlockPos.iterate(feet.add(-3, -2, -3), feet.add(3, 2, 3))) {
            if (p.equals(feet) || p.equals(feet.up())) continue;
            if (!world.getBlockState(p).isReplaceable()) continue;
            BlockPos below = p.down();
            if (!world.getBlockState(below).isSideSolidFullSquare(world, below, Direction.UP)) continue;
            if (!inReach(p)) continue;
            spots.add(p.toImmutable());
        }
        spots.sort(java.util.Comparator.comparingDouble(p -> p.getSquaredDistance(feet)));
        for (BlockPos spot : spots) {
            BlockPos below = spot.down();
            lookAt(Vec3d.ofCenter(below));
            bot.setSneaking(true);      // so it places instead of using the block below
            BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(below).add(0, 0.5, 0), Direction.UP, below, false);
            ActionResult result = bot.interactionManager.interactBlock(bot, world, bot.getMainHandStack(), Hand.MAIN_HAND, hit);
            bot.setSneaking(false);
            if (result.isAccepted() && world.getBlockState(spot).isOf(block)) {
                bot.swingHand(Hand.MAIN_HAND);
                return spot;
            }
        }
        return null;
    }

    /** The result of placeNearby: where it went, still working on it (both null/false), or given up. */
    public record Placed(BlockPos at, boolean failed) {
        static final Placed WORKING = new Placed(null, false);
        static final Placed FAILED = new Placed(null, true);

        public boolean working() {
            return at == null && !failed;
        }
    }

    private BlockPos relocateTo;
    private int relocations;

    /**
     * Places a block item nearby, as placeBeside; if there's nowhere to put it here (in water,
     * on a steep slope), walks to dry, flat ground close by and tries again, up to 3 times.
     * Call every tick until it's no longer working().
     */
    public Placed placeNearby(Item item, Block block) {
        if (relocateTo != null) {
            if (walkTo(relocateTo, 1.0) == Walk.WALKING) return Placed.WORKING;
            relocateTo = null;
        }
        BlockPos at = placeBeside(item, block);
        if (at != null) {
            relocations = 0;
            return new Placed(at, false);
        }
        if (!Crafting.has(bot, item) || ++relocations > 3) {
            relocations = 0;
            return Placed.FAILED;
        }
        relocateTo = dryGround(8);
        if (relocateTo == null) {
            relocations = 0;
            return Placed.FAILED;
        }
        return Placed.WORKING;
    }

    /** The nearest dry, flat, open spot to stand on, 3 to radius blocks away, or null. */
    private BlockPos dryGround(int radius) {
        ServerWorld world = world();
        BlockPos at = bot.getBlockPos();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.iterate(at.add(-radius, -4, -radius), at.add(radius, 3, radius))) {
            double d = p.getSquaredDistance(at);
            if (d < 9 || d >= bestD) continue;
            if (!world.getBlockState(p).isAir() || !world.getBlockState(p.up()).isAir()) continue;
            BlockPos below = p.down();
            if (!world.getBlockState(below).isSideSolidFullSquare(world, below, Direction.UP)) continue;
            if (!world.getFluidState(below).isEmpty()) continue;
            // Room around it to put something down.
            int flat = 0;
            for (Direction dir : Direction.Type.HORIZONTAL) {
                BlockPos n = p.offset(dir);
                if (world.getBlockState(n).isAir() && world.getBlockState(n.down()).isSideSolidFullSquare(world, n.down(), Direction.UP)) flat++;
            }
            if (flat < 2) continue;
            best = p.toImmutable();
            bestD = d;
        }
        return best;
    }

    /** The nearest block of this kind within the given distance, or null. */
    public BlockPos findNear(Block block, int radius) {
        ServerWorld world = world();
        BlockPos at = bot.getBlockPos();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.iterate(at.add(-radius, -radius, -radius), at.add(radius, radius, radius))) {
            if (world.getBlockState(p).isOf(block)) {
                double d = p.getSquaredDistance(at);
                if (d < bestD) {
                    bestD = d;
                    best = p.toImmutable();
                }
            }
        }
        return best;
    }

    /** Within reach of a block (from its eyes)? */
    public boolean inReach(BlockPos pos) {
        return bot.getEyePos().distanceTo(Vec3d.ofCenter(pos)) <= BotConfig.get().reach;
    }

    // ------------------------------------------------------------------ Phase 2 helpers

    /** How many items pass the test, across its 36 slots. */
    public int count(java.util.function.Predicate<ItemStack> test) {
        int n = 0;
        PlayerInventory inv = bot.getInventory();
        for (int i = 0; i < 36; i++) if (!inv.getStack(i).isEmpty() && test.test(inv.getStack(i))) n += inv.getStack(i).getCount();
        return n;
    }

    public int count(Item item) {
        return count(st -> st.isOf(item));
    }

    public boolean has(java.util.function.Predicate<ItemStack> test) {
        return slot(test) >= 0;
    }

    /** The first slot holding something that passes the test, or -1. */
    public int slot(java.util.function.Predicate<ItemStack> test) {
        PlayerInventory inv = bot.getInventory();
        for (int i = 0; i < 36; i++) if (!inv.getStack(i).isEmpty() && test.test(inv.getStack(i))) return i;
        return -1;
    }

    public boolean hold(java.util.function.Predicate<ItemStack> test) {
        int s = slot(test);
        if (s < 0) return false;
        holdSlot(s);
        return true;
    }

    /** Uses what it holds (eat, drink, fill or pour a bucket where it's looking, throw). */
    public boolean useHeld() {
        ActionResult r = bot.interactionManager.interactItem(bot, world(), bot.getMainHandStack(), Hand.MAIN_HAND);
        return r.isAccepted();
    }

    /** Clicks a block face with what it holds (lights a torch on fire, opens, sleeps in a bed). */
    public boolean useOn(BlockPos pos) {
        Vec3d eye = bot.getEyePos();
        Direction face = Direction.getFacing(eye.x - (pos.getX() + 0.5), eye.y - (pos.getY() + 0.5), eye.z - (pos.getZ() + 0.5));
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pos).add(Vec3d.of(face.getVector()).multiply(0.5)), face, pos, false);
        ActionResult r = bot.interactionManager.interactBlock(bot, world(), bot.getMainHandStack(), Hand.MAIN_HAND, hit);
        bot.swingHand(Hand.MAIN_HAND);
        return r.isAccepted();
    }

    /** Places the block item it holds into one exact spot, against any solid neighbour. */
    public boolean placeAt(BlockPos target) {
        ServerWorld world = world();
        if (!world.getBlockState(target).isReplaceable() || !inReach(target)) return false;
        for (Direction dir : Direction.values()) {
            BlockPos against = target.offset(dir);
            BlockState there = world.getBlockState(against);
            if (!there.isSideSolidFullSquare(world, against, dir.getOpposite())) continue;
            Direction face = dir.getOpposite();
            lookAt(Vec3d.ofCenter(against));
            bot.setSneaking(true);
            BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(against).add(Vec3d.of(face.getVector()).multiply(0.5)), face, against, false);
            ActionResult r = bot.interactionManager.interactBlock(bot, world, bot.getMainHandStack(), Hand.MAIN_HAND, hit);
            bot.setSneaking(false);
            if (r.isAccepted() && !world.getBlockState(target).isReplaceable()) {
                bot.swingHand(Hand.MAIN_HAND);
                return true;
            }
        }
        return false;
    }

    /** Runs flat out the way it's facing, hopping anything in the way. */
    public void run() {
        path = null;
        goal = null;
        bot.forwardSpeed = 1F;
        bot.sidewaysSpeed = 0F;
        bot.setSprinting(bot.getHungerManager().getFoodLevel() > 6);
        bot.setJumping(bot.horizontalCollision || bot.isTouchingWater());
    }

    /** Heads away from a point: turns its back on it and runs. */
    public void flee(Vec3d from) {
        Vec3d away = bot.getPos().subtract(from);
        if (away.horizontalLengthSquared() < 0.01) away = bot.getRotationVector().multiply(-1);
        Vec3d aim = bot.getEyePos().add(new Vec3d(away.x, 0, away.z).normalize().multiply(8));
        lookAt(aim);
        run();
    }

    /** Swings at something: walks into reach, faces it, hits when its swing has charged. True once dead or gone. */
    public boolean attack(net.minecraft.entity.Entity target) {
        if (target == null || target.isRemoved() || (target instanceof net.minecraft.entity.LivingEntity le && le.isDead())) {
            stopMoving();
            return true;
        }
        double d = bot.distanceTo(target);
        lookAt(target.getEyePos().add(0, -0.3, 0));
        if (d > 2.8) {
            walkTo(target.getBlockPos(), 2.0);
            return false;
        }
        halt();
        if (bot.getAttackCooldownProgress(0.5F) >= 0.9F) {
            bot.attack(target);
            bot.swingHand(Hand.MAIN_HAND);
        }
        return false;
    }

    /** Holds its hardest-hitting sword or axe, if it has one. */
    public boolean holdBestWeapon() {
        PlayerInventory inv = bot.getInventory();
        int best = -1;
        float bestDmg = 0F;
        for (int i = 0; i < 36; i++) {
            float dmg = weaponDamage(inv.getStack(i));
            if (dmg > bestDmg) {
                bestDmg = dmg;
                best = i;
            }
        }
        holdSlot(best);
        return best >= 0;
    }

    public static float weaponDamage(ItemStack s) {
        if (s.getItem() instanceof net.minecraft.item.SwordItem sword) return sword.getAttackDamage() + 1F;
        if (s.getItem() instanceof net.minecraft.item.AxeItem axe) return axe.getAttackDamage();
        return 0F;
    }

    /** Throws a whole stack the way it is facing. */
    public void throwSlot(int slot, double speed) {
        PlayerInventory inv = bot.getInventory();
        ItemStack s = inv.getStack(slot);
        if (s.isEmpty()) return;
        inv.setStack(slot, ItemStack.EMPTY);
        net.minecraft.entity.ItemEntity thrown = bot.dropItem(s, false, true);
        if (thrown != null) thrown.setVelocity(bot.getRotationVector().multiply(speed).add(0, 0.15, 0));
        bot.swingHand(Hand.MAIN_HAND);
    }
}
