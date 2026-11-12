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
    /** How good it is with its hands, and its habits. */
    public final Hands hands;
    /** Its head: everything that points its eyes goes through here. */
    final Gaze gaze;
    final java.util.Random random = new java.util.Random();
    /** Blocks it has broken (the watchdog reads this as "getting something done"). */
    int dug;
    /** Its brain (Phase 2): runs whenever no order from /hg bot is running. */
    public final Brain brain;
    private Action action;
    /** While the brain drives, orders' chatter ("made a wooden pickaxe") stays out of chat. */
    boolean quiet;
    /** The last thing it would have said while quiet (shown by /hg bot why). */
    String lastSaid = "";

    public Body(DummyPlayer bot, Traits traits, Hands hands) {
        this.bot = bot;
        this.traits = traits;
        this.hands = hands;
        this.gaze = new Gaze(this);
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
            try {
                brain.tick();
            } catch (Exception e) {
                // Nothing a bot thinks may ever take the server down with it.
                Trial.error(brain, "thinking", e);
                stopMoving();
                stopBreaking();
            }
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

    /** Turns its head toward a point as a person does (quick, then easing in). True once facing it. */
    public boolean lookAt(Vec3d point) {
        return gaze.look(point);
    }

    /** Stops to take a place in: a handful of glances all around. Call every tick while it should. */
    public void lookAround() {
        gaze.lookAround();
    }

    /** Looks straight down (to build under itself). */
    public void lookDown() {
        gaze.turnTo(bot.getYaw(), 90F, 0, 1.0, false);
    }

    /** Aims at something alive: its aim wanders by its Hands. True once facing it. */
    public boolean aimAt(net.minecraft.entity.Entity target) {
        return gaze.aim(target);
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
    /** The furthest node of this path it has reached, and ticks since it last reached a new one. */
    private int lastNode;
    private int nodeStall;
    /** Ticks spent on this walk, and how many it may take before the bot calls it off. */
    private int walkTicks;
    private int walkBudget;
    private int lastStep;
    /** A little hop and sidestep to get off whatever it's caught on. */
    private int wiggle;
    private float wiggleSide;
    /** Places it couldn't get to: until when it won't try again, and where it was standing when it gave up. */
    private record NoWay(long until, Vec3d from) {}
    private final java.util.Map<BlockPos, NoWay> noWay = new java.util.HashMap<>();

    /**
     * Walks toward a spot until within the given distance of its centre. Call every tick; it
     * answers WALKING, ARRIVED or FAILED. It fails when there's no way there, when it stays stuck
     * (after a hop and sidestep, then two fresh routes), or when the walk takes far longer than
     * the distance should. A place it gave up on, it won't try again from the same spot for a while.
     */
    public Walk walkTo(BlockPos target, double withinBlocks) {
        Vec3d centre = Vec3d.ofBottomCenter(target);
        if (bot.getPos().distanceTo(centre) <= withinBlocks) {
            stopMoving();
            return Walk.ARRIVED;
        }
        long now = world().getTime();
        if (goal == null || !goal.equals(target)) {
            if (knownNoWay(target, now)) return fail();
            goal = target.toImmutable();
            within = withinBlocks;
            replans = 0;
            noProgress = 0;
            nodeStall = 0;
            lastNode = 0;
            wiggle = 0;
            walkTicks = 0;
            lastStep = 0;
            walkBudget = 100 + (int) (bot.getPos().distanceTo(centre) * 16);
            lastProgressAt = bot.getPos();
            if (!plan()) {
                noWay.put(goal, new NoWay(now + 200, bot.getPos()));
                deadEnd(now);
                return fail();
            }
        }
        if (++walkTicks > walkBudget) return giveUp(now, "taking far too long");
        if (path == null || node >= path.getLength()) {
            double hx = centre.x - bot.getX(), hz = centre.z - bot.getZ();
            if (path != null && hx * hx + hz * hz < 2.6 && Math.abs(centre.y - bot.getY()) < 1.3) {
                // Its path has brought it to the very block: the last step it just takes (for a second or so).
                if (++lastStep > 30) return giveUp(now, "no way on");
                move(hx, hz, 0.6F);
                bot.setJumping(bot.horizontalCollision);
                bot.setSprinting(false);
                return Walk.WALKING;
            }
            // End of a leg (or a partial path): plan the next one from here.
            if (!plan() || goesNowhere()) return giveUp(now, "no way on");
            lastNode = 0;
        }
        // Stuck? First a hop and a sidestep; then a fresh route; then it gives up.
        if (node > lastNode) {
            lastNode = node;
            nodeStall = 0;
        } else if ((++nodeStall == 14 || nodeStall == 30) && !bot.isTouchingWater()) {
            wiggle = 4 + random.nextInt(3);
            wiggleSide = random.nextBoolean() ? 1F : -1F;
        }
        if (bot.getPos().squaredDistanceTo(lastProgressAt) > 1.0) {
            lastProgressAt = bot.getPos();
            noProgress = 0;
        } else if (++noProgress > Math.min(45, BotConfig.get().stuckTicks)) {
            noProgress = 0;
            nodeStall = 0;
            if (++replans > 2 || !plan() || goesNowhere()) return giveUp(now, "stuck");
            lastNode = 0;
        }
        follow(centre);
        return Walk.WALKING;
    }

    /** The route it just worked out ends right where it stands: this is as near as it can get. */
    private boolean goesNowhere() {
        if (path == null || goal == null) return false;
        BlockPos end = path.getNodePos(path.getLength() - 1);
        return end.getSquaredDistance(bot.getBlockPos()) <= 2.0 && Math.sqrt(end.getSquaredDistance(goal)) > within + 1.5;
    }

    /** Calls a walk off, and remembers not to try that place again from here for 40 s. */
    private Walk giveUp(long now, String why) {
        if (goal != null) {
            noWay.put(goal, new NoWay(now + 800, bot.getPos()));
            if (noWay.size() > 32) noWay.values().removeIf(n -> n.until() < now);
            if (noWay.size() > 32) noWay.clear();
            if (why.startsWith("no way")) deadEnd(now);
            else brain.walkGivenUp(goal, why);
        }
        return fail();
    }

    private BlockPos deadEndAt;
    private int deadEnds;
    private long deadEndSince;

    /**
     * It tried to walk somewhere and there was no way even to start. Five of those from the very
     * same block inside 20 s, each to a different place, and it's boxed in: its brain is told.
     */
    private void deadEnd(long now) {
        BlockPos at = bot.getBlockPos();
        if (!at.equals(deadEndAt) || now - deadEndSince > 400) {
            deadEndAt = at;
            deadEndSince = now;
            deadEnds = 0;
        }
        if (++deadEnds >= 5) {
            deadEnds = 0;
            deadEndAt = null;
            brain.boxedIn = true;
        }
    }

    /** Did it give up on this place (or one right by it) a moment ago, from about where it stands now? */
    private boolean knownNoWay(BlockPos target, long now) {
        if (noWay.isEmpty()) return false;
        for (java.util.Map.Entry<BlockPos, NoWay> e : noWay.entrySet()) {
            NoWay n = e.getValue();
            if (n.until() < now) continue;
            if (e.getKey().getSquaredDistance(target) <= 2.0 && n.from().squaredDistanceTo(bot.getPos()) <= 36.0) return true;
        }
        return false;
    }

    /** The watchdog found it going nowhere: whatever it was walking to is struck off for a while. */
    void strikeOffGoal() {
        if (goal != null) noWay.put(goal, new NoWay(world().getTime() + 800, bot.getPos()));
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

    private int hopWait;
    private int hopUntil, hopAgain;

    private void follow(Vec3d finalCentre) {
        // Skip the nodes we're already on or past.
        while (node < path.getLength()) {
            BlockPos n = path.getNodePos(node);
            double ndx = n.getX() + 0.5 - bot.getX(), ndz = n.getZ() + 0.5 - bot.getZ();
            boolean level = Math.abs(bot.getY() - n.getY()) < 1.2;
            if (ndx * ndx + ndz * ndz < 0.35 && level) {
                node++;
                continue;
            }
            // Already past it on the way to the next one (it cut the corner): no turning back for it.
            if (level && node + 1 < path.getLength()) {
                BlockPos m = path.getNodePos(node + 1);
                double sx = m.getX() - n.getX(), sz = m.getZ() - n.getZ();
                double slen = Math.sqrt(sx * sx + sz * sz);
                if (slen > 0.5) {
                    double along = -(ndx * sx + ndz * sz) / slen;
                    double side = Math.abs(ndx * sz - ndz * sx) / slen;
                    if (along > 0.15 && side < 0.45) {
                        node++;
                        continue;
                    }
                }
            }
            break;
        }
        if (node >= path.getLength()) {
            halt();
            return;
        }
        BlockPos n = path.getNodePos(node);
        boolean wet = bot.isTouchingWater() || bot.isInLava();
        // Its eyes: a couple of steps further along than its feet, as a person looks where they're going.
        BlockPos f = path.getNodePos(Math.min(path.getLength() - 1, node + 2));
        boolean flat = f.getY() == n.getY() && n.getY() == bot.getBlockY();
        Vec3d ahead = new Vec3d(f.getX() + 0.5, f.getY() + bot.getStandingEyeHeight() - (flat ? 0.25 : 0.0), f.getZ() + 0.5);
        if (ahead.squaredDistanceTo(bot.getEyePos()) < 1.0) {
            ahead = new Vec3d(n.getX() + 0.5, n.getY() + bot.getStandingEyeHeight(), n.getZ() + 0.5);
        }
        gaze.walking(ahead, flat && !wet && bot.isOnGround() && wiggle == 0 && !brain.eyesBusy());
        // Its legs: toward the next step of the path, whichever way its head is turned.
        double ndx = n.getX() + 0.5 - bot.getX(), ndz = n.getZ() + 0.5 - bot.getZ();
        float want = (float) (MathHelper.atan2(ndz, ndx) * (180.0 / Math.PI)) - 90.0F;
        float off = Math.abs(MathHelper.wrapDegrees(want - bot.getYaw()));
        float forward = move(ndx, ndz, off > 120F ? 0.5F : 1.0F);
        if (wiggle > 0) {
            wiggle--;
            bot.sidewaysSpeed += wiggleSide * 0.5F;
        }
        boolean jump = n.getY() > bot.getBlockY() || bot.horizontalCollision || wet || wiggle > 0;
        double left = bot.getPos().distanceTo(finalCentre);
        int food = bot.getHungerManager().getFoodLevel();
        boolean sprint = !noSprint && left > BotConfig.get().sprintOver && !wet && food > 6
                && forward > 0.5F && off < (bot.isSprinting() ? 75F : 30F);
        // Hoppers sprint-jump in bursts over open, level ground (it's quicker, and hungrier).
        if (sprint && hands.hopper && flat && bot.isOnGround() && left > 10 && food > 14 && straightAhead(4)) {
            if (bot.age > hopAgain) {
                hopUntil = bot.age + 50 + random.nextInt(80);
                hopAgain = hopUntil + 100 + random.nextInt(240);
            }
            if (bot.age < hopUntil && --hopWait <= 0) {
                jump = true;
                hopWait = 2 + random.nextInt(6);
            }
        }
        bot.setJumping(jump);
        bot.setSprinting(sprint);
    }

    /** Are the next few steps of its path level and in one straight line? */
    private boolean straightAhead(int steps) {
        if (path == null || node + steps >= path.getLength()) return false;
        BlockPos a = path.getNodePos(node), b = path.getNodePos(node + 1);
        int dx = b.getX() - a.getX(), dz = b.getZ() - a.getZ();
        for (int i = 1; i <= steps; i++) {
            BlockPos p = path.getNodePos(node + i - 1), q = path.getNodePos(node + i);
            if (q.getY() != a.getY() || q.getX() - p.getX() != dx || q.getZ() - p.getZ() != dz) return false;
        }
        return true;
    }

    /**
     * Points its legs along a direction on the ground, whichever way its head is turned: forward
     * and sideways keys together, as a player strafes. Returns how much of it is forward (-1 to 1).
     */
    private float move(double dx, double dz, float pace) {
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-4) {
            bot.forwardSpeed = 0F;
            bot.sidewaysSpeed = 0F;
            return 0F;
        }
        float rad = bot.getYaw() * 0.017453292F;
        double fx = -MathHelper.sin(rad), fz = MathHelper.cos(rad);
        float forward = (float) ((dx * fx + dz * fz) / len);
        float sideways = (float) ((dx * fz - dz * fx) / len);
        bot.forwardSpeed = forward * pace;
        bot.sidewaysSpeed = sideways * pace;
        return forward;
    }

    /**
     * Heads straight for a point without a path, whichever way its head is turned: for the last
     * few blocks to something it can see, as a player just runs at it.
     */
    public void steer(Vec3d point, boolean sprint) {
        path = null;
        goal = null;
        float forward = move(point.x - bot.getX(), point.z - bot.getZ(), 1.0F);
        bot.setJumping(bot.horizontalCollision || bot.isTouchingWater());
        bot.setSprinting(sprint && forward > 0.5F && bot.getHungerManager().getFoodLevel() > 6);
    }

    /** Stops its legs for a moment, keeping the walk it is on. */
    public void pause() {
        halt();
    }

    /** Are its legs trying to take it somewhere? (The watchdog asks.) */
    boolean wantsToMove() {
        return goal != null || Math.abs(bot.forwardSpeed) > 0.3F;
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
            if (ok) dug++;
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

    // Footwork in a fight (Fight.melee): which way it's circling and until when; ticks of letting go of forward.
    int strafeUntil;
    float strafeSide = 1F;
    int letGo;
    boolean sprintHit;

    private float fleeYaw;
    private int fleeAge = -100;

    /**
     * Heads away from a point: turns its back on it and runs, picking the clearest way that is
     * still away (not straight into a wall, a pit or lava), and sticking with it for a moment.
     */
    public void flee(Vec3d from) {
        Vec3d away = bot.getPos().subtract(from);
        if (away.horizontalLengthSquared() < 0.01) away = bot.getRotationVector().multiply(-1);
        float straight = (float) (MathHelper.atan2(away.z, away.x) * (180.0 / Math.PI)) - 90.0F;
        int since = bot.age - fleeAge;
        if (since > 8 || since < 0 || (bot.horizontalCollision && since > 3)
                || Math.abs(MathHelper.wrapDegrees(straight - fleeYaw)) > 135F) {
            fleeYaw = openWay(straight);
            fleeAge = bot.age;
        }
        Vec3d dir = Vec3d.fromPolar(0F, fleeYaw);
        lookAt(bot.getEyePos().add(dir.multiply(8)));
        // Its legs are off at once, even while its head is still coming round.
        steer(bot.getPos().add(dir.multiply(8)), true);
    }

    /** The clearest heading near the one it wants: that way if it can run there, else the nearest way round. */
    private float openWay(float want) {
        float[] turns = {0F, 30F, -30F, 60F, -60F, 90F, -90F, 130F, -130F};
        float best = want;
        double bestScore = -1.0E9;
        for (float turn : turns) {
            double score = Math.cos(Math.toRadians(turn)) * 2.0 + clearAhead(want + turn) * 0.75;
            if (score > bestScore) {
                bestScore = score;
                best = want + turn;
            }
        }
        return best;
    }

    /** How many of the next 4 blocks that way it could run over (up or down a step), 0 to 4. Lava or a long drop ends the count. */
    private int clearAhead(float yaw) {
        ServerWorld world = world();
        Vec3d dir = Vec3d.fromPolar(0F, yaw);
        int y = MathHelper.floor(bot.getY() + 0.2);
        int ok = 0;
        for (int k = 1; k <= 4; k++) {
            int x = MathHelper.floor(bot.getX() + dir.x * k), z = MathHelper.floor(bot.getZ() + dir.z * k);
            if (!passable(world, new BlockPos(x, y, z)) || !passable(world, new BlockPos(x, y + 1, z))) {
                // A step up?
                if (!passable(world, new BlockPos(x, y + 1, z)) || !passable(world, new BlockPos(x, y + 2, z))) break;
                y++;
            } else {
                int drop = 0;
                while (drop < 4 && passable(world, new BlockPos(x, y - 1, z))
                        && world.getFluidState(new BlockPos(x, y - 1, z)).isEmpty()) {
                    y--;
                    drop++;
                }
                if (drop >= 4) break;
            }
            if (world.getFluidState(new BlockPos(x, y, z)).isIn(net.minecraft.registry.tag.FluidTags.LAVA)
                    || world.getFluidState(new BlockPos(x, y - 1, z)).isIn(net.minecraft.registry.tag.FluidTags.LAVA)) break;
            ok++;
        }
        return ok;
    }

    private static boolean passable(ServerWorld world, BlockPos pos) {
        return world.getBlockState(pos).getCollisionShape(world, pos).isEmpty();
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

    /** Where it's walking to right now, or null. */
    public BlockPos goal() {
        return goal;
    }

    /**
     * Holds something plain: an empty hand if a hotbar slot is free, else anything that is not a
     * light, an igniter or a bow (clicking a lit torch with that snuffs it).
     */
    public void holdPlain() {
        PlayerInventory inv = bot.getInventory();
        for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).isEmpty()) {
                inv.selectedSlot = i;
                return;
            }
        }
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getStack(i);
            if (!net.unbeta.content.light.HeldLight.isUnlit(s) && !net.unbeta.content.light.HeldLight.isIgniter(s)
                    && !(s.getItem() instanceof net.minecraft.item.RangedWeaponItem)) {
                holdSlot(i);
                return;
            }
        }
    }

    /** Swaps an inventory slot with its off hand (where a light goes, leaving the main hand for tools). */
    public void toOffhand(int slot) {
        PlayerInventory inv = bot.getInventory();
        ItemStack off = inv.offHand.get(0);
        inv.offHand.set(0, inv.getStack(slot));
        inv.setStack(slot, off);
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
