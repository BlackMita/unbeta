package net.unbeta.content.jackolantern;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Tracks all lit JoL block positions and every second:
 * 1. Syncs them to client (keeps wthit bar live)
 * 2. Extinguishes any that have passed their burnout deadline
 */
public final class JackOLanternBlockSync {

    // Positions of all currently lit JoL blocks, keyed by world registry key + pos
    private static final Set<Long> LIT_POSITIONS = Collections.synchronizedSet(new HashSet<>());

    private JackOLanternBlockSync() {}

    public static void trackLit(BlockPos pos) { LIT_POSITIONS.add(pos.asLong()); }
    public static void trackUnlit(BlockPos pos) { LIT_POSITIONS.remove(pos.asLong()); }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(JackOLanternBlockSync::tick);
    }

    private static void tick(ServerWorld world) {
        long now = world.getTime();
        if (now % 20 != 0) return;

        LIT_POSITIONS.removeIf(key -> {
            BlockPos pos = BlockPos.fromLong(key);
            if (!world.isChunkLoaded(pos)) return false;
            BlockState state = world.getBlockState(pos);
            boolean isLit = state.contains(JackOLanternLogic.LIT)
                    && state.get(JackOLanternLogic.LIT);
            if (!isLit) return true; // remove stale entry

            if (!(world.getBlockEntity(pos) instanceof JackOLanternBlockEntity jol)) return true;
            long burnoutAt = jol.getBurnoutAt();

            if (burnoutAt >= 0 && now >= burnoutAt) {
                JackOLanternLogic.extinguishPlaced(world, pos, state);
                return true; // remove — now unlit
            }

            // Sync to client
            world.updateListeners(pos, state, state, 3);
            return false;
        });
    }
}
