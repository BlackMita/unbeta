package net.unbeta.core.sched;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-world persistent storage for scheduled POSITION callbacks ("in N ticks, run
 * handler H at block P"). Backed by Minecraft's PersistentState so it saves and loads
 * with the world for free.
 *
 * <p>This holds only DATA, never behaviour. Handlers live in
 * {@link UnbetaScheduler}'s registry and are matched by string id at fire time, which
 * is what lets tasks survive a restart: the id is serialisable, a lambda is not.
 */
public class ScheduledTaskState extends PersistentState {

    /** Save id; becomes data/unbeta_scheduler.dat inside the world folder. */
    public static final String ID = "unbeta_scheduler";

    public static final class Task {
        public final String handlerId;
        public final BlockPos pos;
        public long ticksRemaining;
        public final NbtCompound payload;

        public Task(String handlerId, BlockPos pos, long ticksRemaining, NbtCompound payload) {
            this.handlerId = handlerId;
            this.pos = pos;
            this.ticksRemaining = ticksRemaining;
            this.payload = payload == null ? new NbtCompound() : payload;
        }
    }

    public final List<Task> tasks = new ArrayList<>();

    public ScheduledTaskState() {}

    /** Reader used by PersistentStateManager#getOrCreate. */
    public static ScheduledTaskState fromNbt(NbtCompound nbt) {
        ScheduledTaskState state = new ScheduledTaskState();
        NbtList list = nbt.getList("tasks", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound t = list.getCompound(i);
            state.tasks.add(new Task(
                    t.getString("handler"),
                    new BlockPos(t.getInt("x"), t.getInt("y"), t.getInt("z")),
                    t.getLong("ticks"),
                    t.getCompound("payload")
            ));
        }
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();
        for (Task task : tasks) {
            NbtCompound t = new NbtCompound();
            t.putString("handler", task.handlerId);
            t.putInt("x", task.pos.getX());
            t.putInt("y", task.pos.getY());
            t.putInt("z", task.pos.getZ());
            t.putLong("ticks", task.ticksRemaining);
            t.put("payload", task.payload);
            list.add(t);
        }
        nbt.put("tasks", list);
        return nbt;
    }
}
