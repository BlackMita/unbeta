package net.unbeta.content.metal;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

/** Per world: where the gold blocks are, and how often each diamond block has repaired something. */
public final class BlockMemory extends PersistentState {

    final LongOpenHashSet gold = new LongOpenHashSet();
    final Long2IntOpenHashMap diamondUses = new Long2IntOpenHashMap();

    public static BlockMemory get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(BlockMemory::read, BlockMemory::new, "unbeta_metal_blocks");
    }

    private static BlockMemory read(NbtCompound nbt) {
        BlockMemory m = new BlockMemory();
        for (long p : nbt.getLongArray("Gold")) m.gold.add(p);
        long[] dp = nbt.getLongArray("DiamondPos");
        int[] du = nbt.getIntArray("DiamondUses");
        for (int i = 0; i < Math.min(dp.length, du.length); i++) m.diamondUses.put(dp[i], du[i]);
        return m;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.putLongArray("Gold", gold.toLongArray());
        nbt.putLongArray("DiamondPos", diamondUses.keySet().toLongArray());
        nbt.putIntArray("DiamondUses", diamondUses.values().toIntArray());
        return nbt;
    }
}
