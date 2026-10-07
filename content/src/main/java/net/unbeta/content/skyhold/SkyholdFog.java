package net.unbeta.content.skyhold;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.gen.structure.Structure;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Thick fog around Skyholds, shaped like a donut: clear at the very centre, thickest in the
 * ring 58-85 blocks out, gone past 140. It is full between y 200 and 300, thins above, fades
 * to barely-there at sea level, and is gone 25 blocks below it. Twice a second the server
 * works out each player's strength and tells their game, which fades smoothly towards it.
 */
public final class SkyholdFog {

    public static final Identifier PACKET = new Identifier("unbeta-content", "skyhold_fog");

    /** Distance from the Skyhold's centre -> strength. */
    private static final double[] RING_D = {0, 8, 25, 45, 58, 85, 100, 115, 130, 140};
    private static final double[] RING_S = {0.35, 0.38, 0.5, 0.75, 1, 1, 0.7, 0.35, 0.12, 0};  // centre never clear

    /** Caps how thick the fog gets: 1.0 = full (~24 blocks visible), 0.5 = half that. */
    private static final float MAX_STRENGTH = 0.5f;

    private static final Map<UUID, Float> SENT = new HashMap<>();

    private SkyholdFog() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 10 != 0) return;
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                float strength = strengthAt(player);
                Float last = SENT.get(player.getUuid());
                if (last != null && Math.abs(last - strength) < 0.02f) continue;
                SENT.put(player.getUuid(), strength);
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeFloat(strength);
                ServerPlayNetworking.send(player, PACKET, buf);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SENT.remove(handler.player.getUuid()));
    }

    private static float strengthAt(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        if (world.getRegistryKey() != World.OVERWORLD) return 0f;
        float vertical = vertical(player.getY(), world.getSeaLevel());
        if (vertical <= 0f) return 0f;
        Structure skyhold = world.getRegistryManager().get(RegistryKeys.STRUCTURE).get(Skyhold.ID);
        if (skyhold == null) return 0f;

        // Skyholds touching this chunk or any loaded chunk within 2 - reaches the faint outer edge.
        ChunkPos here = player.getChunkPos();
        LongSet starts = new LongOpenHashSet();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                WorldChunk chunk = world.getChunkManager().getWorldChunk(here.x + dx, here.z + dz);
                if (chunk != null) starts.addAll(chunk.getStructureReferences(skyhold));
            }
        }
        float best = 0f;
        for (long start : starts) {
            ChunkPos origin = new ChunkPos(start);
            double ox = player.getX() - (origin.getStartX() + 8);
            double oz = player.getZ() - (origin.getStartZ() + 8);
            best = Math.max(best, curve(Math.sqrt(ox * ox + oz * oz), RING_D, RING_S));
        }
        return best * vertical * MAX_STRENGTH;
    }

    /** Height -> strength: none deep underground, faint at sea level, full from y 200 to 300. */
    private static float vertical(double y, int seaLevel) {
        double sea = Math.min(seaLevel, 120);
        return curve(y, new double[]{sea - 25, sea, 140, 200, 300, 320},
                        new double[]{0, 0.15, 0.45, 1, 1, 0.6});
    }

    private static float curve(double x, double[] xs, double[] ys) {
        if (x <= xs[0]) return (float) ys[0];
        for (int i = 1; i < xs.length; i++) {
            if (x <= xs[i]) {
                double f = (x - xs[i - 1]) / (xs[i] - xs[i - 1]);
                return (float) (ys[i - 1] + (ys[i] - ys[i - 1]) * f);
            }
        }
        return (float) ys[ys.length - 1];
    }
}
