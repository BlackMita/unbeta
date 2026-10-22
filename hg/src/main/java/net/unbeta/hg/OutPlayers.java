package net.unbeta.hg;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.S2CPlayChannelEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LightBlock;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Out players are ghosts: everyone sees them partly see-through (drawn by the Unbeta HG jar on
 * players' games, so it's told who is out), and they glow as brightly as a redstone torch
 * (light level 7) wherever they walk - an invisible light block that follows them about.
 */
public final class OutPlayers {

    private OutPlayers() {}

    public static final Identifier SYNC = new Identifier(UnbetaHG.MOD_ID, "out");
    private static final int GLOW = 7;

    private record Light(ServerWorld world, BlockPos pos) {}

    private static final Map<UUID, Light> lights = new HashMap<>();

    public static void register() {
        // When a player's game says it understands us, tell it who is out and its keeps - on the
        // next tick: this can fire while the connection is still being set up, too early to send.
        S2CPlayChannelEvents.REGISTER.register((handler, sender, server, channels) -> {
            if (channels.contains(SYNC) || channels.contains(Keeps.SYNC)) pending.add(handler.player.getUuid());
        });
    }

    private static final java.util.Set<UUID> pending = new java.util.HashSet<>();

    // ------------------------------------------------------------------ the glow

    public static void tick(MinecraftServer server) {
        if (!pending.isEmpty()) {
            for (UUID id : new ArrayList<>(pending)) {
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
                if (p != null) {
                    sync(p);
                    Keeps.sync(p);
                }
            }
            pending.clear();
        }
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (Round.isOut(p) && p.isAlive() && !p.isSpectator()) follow(p);
            else unlight(p.getUuid());
        }
        // Players who left.
        for (Iterator<Map.Entry<UUID, Light>> it = lights.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Light> e = it.next();
            if (server.getPlayerManager().getPlayer(e.getKey()) == null) {
                clear(e.getValue());
                it.remove();
            }
        }
    }

    private static void follow(ServerPlayerEntity p) {
        ServerWorld world = p.getServerWorld();
        BlockPos eyes = BlockPos.ofFloored(p.getEyePos());
        BlockPos at = world.getBlockState(eyes).isAir() || isOurs(world.getBlockState(eyes)) ? eyes
                : world.getBlockState(p.getBlockPos()).isAir() || isOurs(world.getBlockState(p.getBlockPos())) ? p.getBlockPos() : null;
        Light old = lights.get(p.getUuid());
        if (old != null && old.world == world && old.pos.equals(at)) return;
        if (old != null) clear(old);
        if (at == null) {
            lights.remove(p.getUuid());
            return;
        }
        world.setBlockState(at, Blocks.LIGHT.getDefaultState().with(LightBlock.LEVEL_15, GLOW), Block.NOTIFY_ALL);
        lights.put(p.getUuid(), new Light(world, at.toImmutable()));
    }

    private static void unlight(UUID id) {
        Light old = lights.remove(id);
        if (old != null) clear(old);
    }

    /** Lights out for everyone (the round is over). */
    public static void clearAll() {
        for (Light l : lights.values()) clear(l);
        lights.clear();
    }

    private static boolean isOurs(BlockState state) {
        return state.isOf(Blocks.LIGHT) && state.get(LightBlock.LEVEL_15) == GLOW;
    }

    private static void clear(Light l) {
        if (isOurs(l.world.getBlockState(l.pos))) l.world.setBlockState(l.pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
    }

    // ------------------------------------------------------------------ telling players' games

    public static void syncAll(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) sync(p);
    }

    public static void sync(ServerPlayerEntity p) {
        if (p instanceof DummyPlayer || !ServerPlayNetworking.canSend(p, SYNC)) return;
        List<UUID> out = new ArrayList<>(Round.outPlayers());
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(out.size());
        for (UUID id : out) buf.writeUuid(id);
        ServerPlayNetworking.send(p, SYNC, buf);
    }
}
