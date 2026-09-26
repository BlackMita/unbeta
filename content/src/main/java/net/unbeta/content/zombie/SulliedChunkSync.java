package net.unbeta.content.zombie;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Tells each client which chunks around it are sullied, once a second, so the client
 * can draw spores on their surface (see SulliedSporesMixin). Only the server knows the
 * chunk memory; drawing stays client-side, like vanilla mycelium, so the only network
 * cost is this one small message per player per second.
 */
public final class SulliedChunkSync {

    public static final Identifier CHANNEL = new Identifier("unbeta-content", "sullied_chunks");
    /** Chunks around the player to report. The client's display sampler reaches 32 blocks. */
    private static final int RADIUS = 3;

    private SulliedChunkSync() {}

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(SulliedChunkSync::tick);
    }

    private static void tick(ServerWorld world) {
        if (world.getTime() % 20 != 0) return;
        if (world.getPlayers().isEmpty()) return;
        SulliedChunkState state = SulliedChunkState.getOrCreate(world);

        for (ServerPlayerEntity player : world.getPlayers()) {
            if (!ServerPlayNetworking.canSend(player, CHANNEL)) continue;
            ChunkPos center = new ChunkPos(player.getBlockPos());
            List<Long> near = new ArrayList<>();
            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    ChunkPos cp = new ChunkPos(center.x + dx, center.z + dz);
                    if (state.hasMemory(cp)) near.add(cp.toLong());
                }
            }
            // Sent even when empty, so a chunk that just drained stops puffing.
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeVarInt(near.size());
            for (long key : near) buf.writeLong(key);
            ServerPlayNetworking.send(player, CHANNEL, buf);
        }
    }
}
