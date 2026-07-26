package net.unbeta.core.gates;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.unbeta.core.UnbetaCore;
import net.unbeta.core.api.UnbetaApi;

import java.util.List;

/**
 * Generic, dimension-agnostic gate. Nothing in here names the End specifically.
 *
 * <p>This is deliberate and is the point of the class: Phase 1 gates
 * {@code minecraft:the_end}, and Phase 2 gates {@code minecraft:the_nether} by
 * flipping one rule - with zero new code, because every mixin below asks this
 * service rather than hardcoding a dimension.
 *
 * <p>Layers:
 * <ol>
 *   <li>Portal traversal - the portal-block mixins consult {@link #isGated}.</li>
 *   <li>Portal activation - the activator-item mixins consult {@link #isGated}.</li>
 *   <li>Eviction safety net - a player already inside a gated dimension (old save,
 *       command, another mod) is returned to the overworld spawn.</li>
 * </ol>
 *
 * <p>Never unregisters the dimension. {@code minecraft:the_end} remains a valid,
 * registered dimension key; it is simply unreachable and uninhabitable.
 */
public final class DimensionGate {

    private DimensionGate() {}

    /** True if Unbeta has removed this dimension. */
    public static boolean isGated(RegistryKey<World> key) {
        if (!UnbetaApi.isReady() || key == null) return false;
        return UnbetaApi.rules().isDimensionRemoved(key);
    }

    public static boolean isGated(World world) {
        return world != null && isGated(world.getRegistryKey());
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(DimensionGate::evictIfGated);
        UnbetaCore.LOG.info("Dimension gate active. the_end gated: {} | the_nether gated: {}",
                isGated(World.END), isGated(World.NETHER));
    }

    /**
     * Runs at the end of each world tick, but returns immediately for every world
     * that is not gated - so the cost on a normal overworld tick is one boolean.
     */
    private static void evictIfGated(ServerWorld world) {
        if (!isGated(world.getRegistryKey())) return;

        List<ServerPlayerEntity> players = world.getPlayers();
        if (players.isEmpty()) return;

        ServerWorld overworld = world.getServer().getOverworld();
        if (overworld == null) return;

        BlockPos spawn = overworld.getSpawnPos();
        for (ServerPlayerEntity player : List.copyOf(players)) {
            player.teleport(overworld,
                    spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                    player.getYaw(), player.getPitch());
            UnbetaCore.LOG.info("Evicted {} from gated dimension {}",
                    player.getName().getString(), world.getRegistryKey().getValue());
        }
    }
}
