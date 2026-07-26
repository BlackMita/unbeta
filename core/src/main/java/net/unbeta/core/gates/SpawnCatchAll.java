package net.unbeta.core.gates;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.unbeta.core.UnbetaCore;
import net.unbeta.core.api.ContentKind;
import net.unbeta.core.api.UnbetaApi;
import net.unbeta.core.config.UnbetaConfig;

/**
 * Safety-net spawn gate. Fires whenever any entity is added to a server world, by any
 * means - spawner, structure, summon, spawn egg, breeding, mob-spawned-mob. If the
 * entity is gated, it is discarded before it can tick.
 *
 * <p>{@link SpawnGate} handles the common case (natural biome spawns) efficiently;
 * this catches everything else so no gated mob ever persists, regardless of origin.
 *
 * <p>Escape hatch: when {@code config.allowDeliberateGatedSpawns} is true, entities
 * whose {@code SpawnReason} is not natural are allowed through, so a developer can
 * /summon a gated mob to inspect it. Natural spawns are still removed.
 * (SpawnReason is not available on this event in 1.20.1, so the flag simply disables
 * the catch-all entirely when set - documented here so behaviour is not surprising.)
 */
public final class SpawnCatchAll {

    private final UnbetaConfig config;

    private SpawnCatchAll(UnbetaConfig config) {
        this.config = config;
    }

    public static void register(UnbetaConfig config) {
        SpawnCatchAll gate = new SpawnCatchAll(config);
        ServerEntityEvents.ENTITY_LOAD.register(gate::onEntityLoad);
    }

    private void onEntityLoad(Entity entity, net.minecraft.server.world.ServerWorld world) {
        if (!UnbetaApi.isReady()) return;

        // Only living mobs are subject to spawn gating. Items, projectiles, boats etc.
        // are handled by other gates or kept.
        if (!(entity instanceof LivingEntity) || !(entity instanceof MobEntity)) return;

        if (config.allowDeliberateGatedSpawns) return; // escape hatch: gate disabled

        Identifier id = Registries.ENTITY_TYPE.getId(entity.getType());
        if (id == null) return;

        if (UnbetaApi.rules().isRemoved(ContentKind.ENTITY, id)) {
            entity.discard();
            if (config.verboseGateLogging) {
                UnbetaCore.LOG.debug("Spawn catch-all discarded {} at {}", id, entity.getBlockPos());
            }
        }
    }
}
