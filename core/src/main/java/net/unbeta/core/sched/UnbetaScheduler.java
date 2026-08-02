package net.unbeta.core.sched;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;
import net.unbeta.core.UnbetaCore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Unbeta's persisted tick scheduler - a Phase 1 foundation service built for Phase 2.
 *
 * <p>Two independent subsystems:
 *
 * <h2>A. Position callbacks</h2>
 * "In N ticks, run handler H at block P." Stored per-world in {@link ScheduledTaskState}
 * (a vanilla PersistentState), so tasks survive save/load. Handlers are registered by id
 * at mod init - the STRING id is persisted, never the lambda, which is what makes
 * restart-survival possible.
 * <br>Consumer: skeleton bone piles ("re-animate here in 60s").
 *
 * <h2>B. Item countdowns</h2>
 * A tick count stamped into an ItemStack's own NBT, so it rides along with the stack
 * wherever it goes (hand, inventory, chest, dropped) with no extra bookkeeping - NBT is
 * saved and loaded as part of the stack for free.
 * <br>Each server tick we scan player inventories and loaded block-entity inventories and
 * decrement any stamped stack. At zero the registered handler fires WITH THE STACK'S
 * CURRENT LOCATION, so effects land in the right place (e.g. a bomb in a chest explodes
 * at the chest).
 * <br>Consumers: torch durability-while-placed, creeper inventory bombs.
 *
 * <p><b>Deliberate semantics:</b> item countdowns only advance while the stack is in a
 * TICKING container - a loaded player inventory or a block entity in a loaded chunk. An
 * item in an unloaded chunk pauses and resumes when the chunk loads. This was a design
 * choice, not an accident: it keeps the scan bounded, and it means an unwatched bomb
 * waits for you rather than detonating into the void.
 */
public final class UnbetaScheduler {

    /** NBT key stamped onto scheduled item stacks. */
    public static final String NBT_ROOT = "UnbetaTimer";
    private static final String NBT_TICKS = "ticks";
    private static final String NBT_HANDLER = "handler";

    /** Item countdowns advance once per second, not per tick. */
    public static final int ITEM_SCAN_INTERVAL = 20;
    /** Container scan radius around each player, in chunks. */
    public static final int SCAN_CHUNK_RADIUS = 3;

    private static final Map<String, PositionCallback> POSITION_HANDLERS = new HashMap<>();
    private static final Map<String, ItemCountdownCallback> ITEM_HANDLERS = new HashMap<>();

    private UnbetaScheduler() {}

    // ------------------------------------------------------------------ setup

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(UnbetaScheduler::tickWorld);
        UnbetaCore.LOG.info("Tick scheduler active (position callbacks + item countdowns, persisted).");
    }

    /** Register a handler for position tasks. Call at mod init, before any task fires. */
    public static void registerPositionHandler(String id, PositionCallback callback) {
        POSITION_HANDLERS.put(id, callback);
    }

    /** Register a handler for item countdowns. Call at mod init. */
    public static void registerItemHandler(String id, ItemCountdownCallback callback) {
        ITEM_HANDLERS.put(id, callback);
    }

    // -------------------------------------------------- A. position callbacks

    private static ScheduledTaskState stateOf(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(
                ScheduledTaskState::fromNbt,
                ScheduledTaskState::new,
                ScheduledTaskState.ID);
    }

    /** Schedule a handler to run at a block position after a delay. Persisted. */
    public static void schedule(ServerWorld world, BlockPos pos, long delayTicks,
                                String handlerId, NbtCompound payload) {
        ScheduledTaskState state = stateOf(world);
        state.tasks.add(new ScheduledTaskState.Task(handlerId, pos.toImmutable(), delayTicks, payload));
        state.markDirty();
    }

    public static void schedule(ServerWorld world, BlockPos pos, long delayTicks, String handlerId) {
        schedule(world, pos, delayTicks, handlerId, null);
    }

    // ------------------------------------------------------ B. item countdowns

    /** Stamp a countdown onto a stack. The stack carries it wherever it goes. */
    public static void setItemCountdown(ItemStack stack, long ticks, String handlerId) {
        if (stack == null || stack.isEmpty()) return;
        NbtCompound root = new NbtCompound();
        root.putLong(NBT_TICKS, ticks);
        root.putString(NBT_HANDLER, handlerId);
        stack.getOrCreateNbt().put(NBT_ROOT, root);
    }

    /** Remaining ticks, or -1 if this stack has no countdown. */
    public static long getItemCountdown(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasNbt()) return -1;
        NbtCompound nbt = stack.getNbt();
        if (nbt == null || !nbt.contains(NBT_ROOT)) return -1;
        return nbt.getCompound(NBT_ROOT).getLong(NBT_TICKS);
    }

    public static void clearItemCountdown(ItemStack stack) {
        if (stack != null && stack.hasNbt() && stack.getNbt() != null) {
            stack.getNbt().remove(NBT_ROOT);
        }
    }

    // ------------------------------------------------------------- the tick

    private static void tickWorld(ServerWorld world) {
        tickPositionTasks(world);
        tickItemCountdowns(world);
    }

    private static void tickPositionTasks(ServerWorld world) {
        ScheduledTaskState state = stateOf(world);
        if (state.tasks.isEmpty()) return;

        List<ScheduledTaskState.Task> due = new ArrayList<>();
        Iterator<ScheduledTaskState.Task> it = state.tasks.iterator();
        while (it.hasNext()) {
            ScheduledTaskState.Task task = it.next();
            if (--task.ticksRemaining <= 0) {
                due.add(task);
                it.remove();
            }
        }
        if (due.isEmpty()) {
            state.markDirty(); // counters changed
            return;
        }
        for (ScheduledTaskState.Task task : due) {
            PositionCallback cb = POSITION_HANDLERS.get(task.handlerId);
            if (cb == null) {
                UnbetaCore.LOG.warn("Scheduler: no handler registered for id '{}' (task dropped).", task.handlerId);
                continue;
            }
            try {
                cb.run(world, task.pos, task.payload);
            } catch (Throwable t) {
                UnbetaCore.LOG.error("Scheduler: handler '{}' threw at {}", task.handlerId, task.pos, t);
            }
        }
        state.markDirty();
    }

    /**
     * Advances item countdowns. Deliberately CHEAP:
     * <ul>
     *   <li>Runs once per second (every {@value #ITEM_SCAN_INTERVAL} ticks), decrementing by
     *       that amount, rather than every tick. A bomb/torch timer does not need 50ms
     *       precision, and this is 20x less work.</li>
     *   <li>Only scans containers in chunks NEAR A PLAYER ({@value #SCAN_CHUNK_RADIUS}-chunk
     *       radius). 1.20.1 exposes no "iterate all loaded chunks" API anyway, and an
     *       unbounded per-tick block scan is exactly what stalled world-saving once before
     *       in this project. Bounded by construction.</li>
     * </ul>
     * Consequence (intended): a stack in an unloaded or far-away container pauses and
     * resumes when a player comes near.
     */
    private static void tickItemCountdowns(ServerWorld world) {
        if (world.getServer().getTicks() % ITEM_SCAN_INTERVAL != 0) return;

        // --- players: hands + full inventory ---
        for (PlayerEntity player : world.getPlayers()) {
            scanInventory(world, player.getInventory(), player, player.getBlockPos());
        }

        // --- containers in chunks near players (makes "bomb in a chest" work) ---
        java.util.Set<Long> visited = new java.util.HashSet<>();
        for (PlayerEntity player : world.getPlayers()) {
            int cx = player.getBlockPos().getX() >> 4;
            int cz = player.getBlockPos().getZ() >> 4;
            for (int dx = -SCAN_CHUNK_RADIUS; dx <= SCAN_CHUNK_RADIUS; dx++) {
                for (int dz = -SCAN_CHUNK_RADIUS; dz <= SCAN_CHUNK_RADIUS; dz++) {
                    int x = cx + dx, z = cz + dz;
                    long key = (((long) x) << 32) ^ (z & 0xffffffffL);
                    if (!visited.add(key)) continue;                 // don't rescan shared chunks
                    if (!world.getChunkManager().isChunkLoaded(x, z)) continue;
                    WorldChunk chunk = world.getChunkManager().getWorldChunk(x, z);
                    if (chunk == null) continue;
                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        if (be instanceof Inventory inv) {
                            scanInventory(world, inv, null, be.getPos());
                        }
                    }
                }
            }
        }
    }

    private static void scanInventory(ServerWorld world, Inventory inv, Entity holder, BlockPos pos) {
        int size = inv.size();
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = inv.getStack(slot);
            if (stack.isEmpty() || !stack.hasNbt()) continue;
            NbtCompound nbt = stack.getNbt();
            if (nbt == null || !nbt.contains(NBT_ROOT)) continue;

            NbtCompound root = nbt.getCompound(NBT_ROOT);
            long ticks = root.getLong(NBT_TICKS) - ITEM_SCAN_INTERVAL;
            if (ticks > 0) {
                root.putLong(NBT_TICKS, ticks);
                continue;
            }

            // expired
            String handlerId = root.getString(NBT_HANDLER);
            ItemCountdownCallback cb = ITEM_HANDLERS.get(handlerId);
            boolean consume = false;
            if (cb == null) {
                UnbetaCore.LOG.warn("Scheduler: no item handler '{}' (countdown cleared).", handlerId);
                nbt.remove(NBT_ROOT);
            } else {
                try {
                    consume = cb.onExpire(world, stack, holder, pos);
                } catch (Throwable t) {
                    UnbetaCore.LOG.error("Scheduler: item handler '{}' threw at {}", handlerId, pos, t);
                }
                nbt.remove(NBT_ROOT);
            }
            if (consume) {
                inv.setStack(slot, ItemStack.EMPTY);
            }
            inv.markDirty();
        }
    }
}
