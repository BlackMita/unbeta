package net.unbeta.content.lockey;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * Every block entity in every currently-loaded chunk of a world.
 *
 * <p>ServerWorld exposes no such accessor, so we walk the chunk storage's holders and
 * read each loaded chunk's block-entity map. entryIterator() is protected in vanilla and
 * is opened by our access widener.
 *
 * <p>This snapshots into a list rather than streaming lazily: the caller may modify
 * inventories while iterating, and doing that over a live view invites concurrent
 * modification. The cost is one ArrayList per call, which is fine for an on-demand
 * search triggered by a player clicking a chest - do NOT call this every tick.
 */
public final class LoadedBlockEntities {

    private LoadedBlockEntities() {}

    public static List<BlockEntity> iterate(ServerWorld world) {
        List<BlockEntity> out = new ArrayList<>();
        for (ChunkHolder holder : world.getChunkManager().threadedAnvilChunkStorage.entryIterator()) {
            WorldChunk chunk = holder.getWorldChunk();
            if (chunk == null) continue;
            out.addAll(chunk.getBlockEntities().values());
        }
        return out;
    }
}
