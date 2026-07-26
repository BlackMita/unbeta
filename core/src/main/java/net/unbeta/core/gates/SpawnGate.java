package net.unbeta.core.gates;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.unbeta.core.UnbetaCore;
import net.unbeta.core.api.ContentKind;
import net.unbeta.core.api.UnbetaApi;

import java.util.ArrayList;
import java.util.List;

/**
 * Primary spawn gate: strips gated entities from every biome's natural spawn tables.
 *
 * <p>Uses {@link BiomeModifications} in the {@link ModificationPhase#REMOVALS} phase,
 * which removes spawn ENTRIES rather than overwriting biome JSON. That is what lets
 * this coexist with Moderner Beta and any biome mod - we compose with their biomes
 * instead of clobbering them.
 *
 * <p>This does NOT catch spawners, structure spawns, summons, breeding, or mob-spawned
 * mobs. Those go through {@link SpawnCatchAll}. Two layers on purpose.
 *
 * <p>The set of gated entities is resolved ONCE at registration from the manifest.
 * Biome modifications are baked at datapack-load, so a mid-game rule change (via
 * {@code /unbeta reload}) will not retro-actively alter already-modified biomes - a
 * world reload picks it up. This is acceptable: spawn rules are a world-creation
 * concern, and the catch-all covers correctness in the meantime.
 */
public final class SpawnGate {

    private SpawnGate() {}

    public static void register() {
        if (!UnbetaApi.isReady()) return;

        List<EntityType<?>> gated = collectGatedEntities();
        if (gated.isEmpty()) {
            UnbetaCore.LOG.info("Spawn gate: nothing to remove from biome spawn tables.");
            return;
        }

        // One modifier for all gated types, applied to every biome. REMOVALS phase.
        BiomeModifications.create(new Identifier("unbeta", "remove_gated_spawns"))
            .add(ModificationPhase.REMOVALS,
                 BiomeSelectors.all(),
                 (selection, context) -> {
                     for (EntityType<?> type : gated) {
                         context.getSpawnSettings().removeSpawnsOfEntityType(type);
                     }
                 });

        UnbetaCore.LOG.info("Spawn gate: removed {} entity type(s) from biome spawn tables.", gated.size());
    }

    private static List<EntityType<?>> collectGatedEntities() {
        List<EntityType<?>> out = new ArrayList<>();
        for (EntityType<?> type : Registries.ENTITY_TYPE) {
            Identifier id = Registries.ENTITY_TYPE.getId(type);
            if (id == null) continue;
            // Only spawnable mob categories matter for spawn tables.
            if (type.getSpawnGroup() == SpawnGroup.MISC) continue;
            if (UnbetaApi.rules().isRemoved(ContentKind.ENTITY, id)) {
                out.add(type);
            }
        }
        return out;
    }
}
