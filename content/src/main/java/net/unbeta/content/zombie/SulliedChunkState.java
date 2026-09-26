package net.unbeta.content.zombie;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.PersistentState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-world memory of what died in each chunk, waiting to rise back out of the ground.
 *
 * <p>Each sullied chunk holds a queue of entity type ids ("minecraft:zombie" now, the
 * corrupted animals later). SulliedChunkTick drains it one entry per 5-second beat while
 * a player stands in the chunk; the chunk is clean again once its queue is empty.
 *
 * <p>Save format: chunks = [{pos: long, queue: [string...]}]. The legacy format
 * ({pos, time} - a single sully with a ready-time) loads as a queue holding one zombie,
 * so chunks sullied before this change still produce their zombie.
 */
public class SulliedChunkState extends PersistentState {

    public static final String KEY = "unbeta_sullied_chunks";
    public static final String ZOMBIE = "minecraft:zombie";
    /** Deaths beyond this many in one chunk simply aren't remembered. */
    public static final int MAX_REMEMBERED = 16;

    private final Map<Long, List<String>> memory = new HashMap<>();

    public SulliedChunkState() {}

    public static SulliedChunkState getOrCreate(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(
                SulliedChunkState::fromNbt, SulliedChunkState::new, KEY);
    }

    public static SulliedChunkState fromNbt(NbtCompound nbt) {
        SulliedChunkState state = new SulliedChunkState();
        NbtList list = nbt.getList("chunks", 10); // 10 = compound
        for (int i = 0; i < list.size(); i++) {
            NbtCompound e = list.getCompound(i);
            List<String> queue = new ArrayList<>();
            if (e.contains("queue", 9)) { // 9 = list
                NbtList q = e.getList("queue", 8); // 8 = string
                for (int j = 0; j < q.size() && queue.size() < MAX_REMEMBERED; j++) {
                    queue.add(q.getString(j));
                }
            } else {
                // Legacy entry from before chunks had memory: one remembered zombie.
                queue.add(ZOMBIE);
            }
            if (!queue.isEmpty()) state.memory.put(e.getLong("pos"), queue);
        }
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();
        for (var entry : memory.entrySet()) {
            NbtCompound e = new NbtCompound();
            e.putLong("pos", entry.getKey());
            NbtList q = new NbtList();
            for (String id : entry.getValue()) q.add(NbtString.of(id));
            e.put("queue", q);
            list.add(e);
        }
        nbt.put("chunks", list);
        return nbt;
    }

    /** Record a death in this chunk. Ignored once the chunk already holds MAX_REMEMBERED. */
    public void remember(ChunkPos pos, String entityTypeId) {
        List<String> queue = memory.computeIfAbsent(pos.toLong(), k -> new ArrayList<>());
        if (queue.size() >= MAX_REMEMBERED) return;
        queue.add(entityTypeId);
        markDirty();
    }

    public boolean hasMemory(ChunkPos pos) {
        List<String> queue = memory.get(pos.toLong());
        return queue != null && !queue.isEmpty();
    }

    /** Remove and return one remembered entry, chosen at random, or null if none. */
    public String takeRandom(ChunkPos pos, Random random) {
        List<String> queue = memory.get(pos.toLong());
        if (queue == null || queue.isEmpty()) return null;
        String id = queue.remove(random.nextInt(queue.size()));
        if (queue.isEmpty()) memory.remove(pos.toLong());
        markDirty();
        return id;
    }

    public void clear(ChunkPos pos) {
        if (memory.remove(pos.toLong()) != null) markDirty();
    }
}
