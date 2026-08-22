package net.unbeta.content.zombie;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.PersistentState;
import java.util.HashMap;
import java.util.Map;

public class SulliedChunkState extends PersistentState {

    public static final String KEY = "unbeta_sullied_chunks";
    public static final long SULLIED_DURATION = 100L; // TESTING - change back to 6000L

    private final Map<Long, Long> sulliedChunks = new HashMap<>();

    public SulliedChunkState() {}

    public static SulliedChunkState getOrCreate(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(
                SulliedChunkState::fromNbt, SulliedChunkState::new, KEY);
    }

    public static SulliedChunkState fromNbt(NbtCompound nbt) {
        SulliedChunkState state = new SulliedChunkState();
        NbtList list = nbt.getList("chunks", 10);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound e = list.getCompound(i);
            state.sulliedChunks.put(e.getLong("pos"), e.getLong("time"));
        }
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();
        for (var entry : sulliedChunks.entrySet()) {
            NbtCompound e = new NbtCompound();
            e.putLong("pos", entry.getKey());
            e.putLong("time", entry.getValue());
            list.add(e);
        }
        nbt.put("chunks", list);
        return nbt;
    }

    public void sully(ChunkPos pos, long currentTime) {
        sulliedChunks.put(pos.toLong(), currentTime + SULLIED_DURATION);
        markDirty();
    }

    public boolean isReady(ChunkPos pos, long currentTime) {
        Long readyAt = sulliedChunks.get(pos.toLong());
        return readyAt != null && currentTime >= readyAt;
    }

    public void clear(ChunkPos pos) {
        sulliedChunks.remove(pos.toLong());
        markDirty();
    }
}
