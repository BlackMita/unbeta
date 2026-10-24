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

    private static UnbetaConfig config;
    /** Gated mobs seen loading, removed at the end of the tick (never in the middle of their own loading). */
    private static final java.util.Set<Entity> pending = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    private SpawnCatchAll() {}

    public static void register(UnbetaConfig cfg) {
        config = cfg;
        ServerEntityEvents.ENTITY_LOAD.register(SpawnCatchAll::onEntityLoad);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_WORLD_TICK.register(SpawnCatchAll::afterTick);
    }

    /**
     * Is this a mob Unbeta has removed? Used here, and by the spawn hooks (GatedSpawnMixin) that
     * stop such mobs being created at all - villagers, iron golems and cats in village structures,
     * cats from the cat spawner, and so on.
     */
    public static boolean gated(Entity entity) {
        if (config == null || config.allowDeliberateGatedSpawns || !UnbetaApi.isReady()) return false;
        if (!(entity instanceof LivingEntity) || !(entity instanceof MobEntity)) return false;
        Identifier id = Registries.ENTITY_TYPE.getId(entity.getType());
        return id != null && UnbetaApi.rules().isRemoved(ContentKind.ENTITY, id);
    }

    private static void onEntityLoad(Entity entity, net.minecraft.server.world.ServerWorld world) {
        // Discarding an entity in the middle of its own loading could leave it half there: still
        // ticking on the server (opening doors, fighting zombies) but never sent to anyone's game -
        // the ghost villagers and golems. So it's set aside and removed once the tick is over.
        if (gated(entity)) pending.add(entity);
    }

    private static void afterTick(net.minecraft.server.world.ServerWorld world) {
        if (!pending.isEmpty()) {
            for (Entity e : new java.util.ArrayList<>(pending)) {
                if (e.getWorld() != world) continue;
                pending.remove(e);
                if (!e.isRemoved()) {
                    e.discard();
                    if (config.verboseGateLogging) {
                        UnbetaCore.LOG.debug("Spawn catch-all discarded {} at {}", Registries.ENTITY_TYPE.getId(e.getType()), e.getBlockPos());
                    }
                }
            }
        }
        // And every 10 seconds, a sweep for anything gated that got in some other way.
        if (world.getTime() % 200 == 0) {
            java.util.List<Entity> found = new java.util.ArrayList<>();
            for (Entity e : world.iterateEntities()) if (gated(e) && !e.isRemoved()) found.add(e);
            for (Entity e : found) e.discard();
        }
    }
}
