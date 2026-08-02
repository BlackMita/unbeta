package net.unbeta.core.sched;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Handler for a scheduled position task. Registered once at mod init under a stable
 * string id; the scheduler stores the ID (not the lambda), which is what allows tasks
 * to survive save/load.
 */
@FunctionalInterface
public interface PositionCallback {
    void run(ServerWorld world, BlockPos pos, NbtCompound payload);
}
