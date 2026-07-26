package net.unbeta.core.gates;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.feature.PlacedFeature;
import net.unbeta.core.UnbetaCore;
import net.unbeta.core.config.UnbetaConfig;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Strips post-Beta-1.8 placed features from every biome.
 *
 * <p>Moderner Beta owns terrain and biome layout. This gate exists for features that
 * attach to ORDINARY overworld biomes (copper ore, tuff blobs, amethyst geodes, glow
 * lichen), which survive even when cave-biome injection is turned off.
 *
 * <p>Tuff matters more than it looks: vanilla's {@code deepslate_ore_replaceables} tag
 * contains tuff as well as deepslate, so ore features landing in a tuff blob place the
 * DEEPSLATE ore variant. Removing tuff blobs is what stops deepslate ores appearing in
 * a world that has no deepslate.
 *
 * <h2>Why every removal is wrapped in a try/catch</h2>
 * {@code removeFeature} THROWS if the key is not a registered placed feature - it does
 * not return false. Since {@code config.removedPlacedFeatures} is a user-editable list,
 * one typo would otherwise crash world creation. A content gate must never take the
 * game down over a config entry, so unknown keys are skipped and reported once.
 *
 * <p>Use {@code /unbeta features} in game to dump the real placed-feature IDs and build
 * an accurate list.
 */
public final class FeatureGate {

    /** Names that turned out not to exist, logged once rather than once per biome. */
    private static final Set<String> MISSING = new LinkedHashSet<>();
    private static boolean reported = false;

    private FeatureGate() {}

    public static void register(UnbetaConfig config) {
        List<String> paths = config.removedPlacedFeatures;
        if (paths == null || paths.isEmpty()) {
            UnbetaCore.LOG.info("Feature gate: nothing configured to remove.");
            return;
        }

        List<RegistryKey<PlacedFeature>> keys = new ArrayList<>(paths.size());
        for (String path : paths) {
            Identifier id = path.contains(":") ? new Identifier(path) : new Identifier("minecraft", path);
            keys.add(RegistryKey.of(RegistryKeys.PLACED_FEATURE, id));
        }

        BiomeModifications.create(new Identifier("unbeta", "remove_modern_features"))
            .add(ModificationPhase.REMOVALS,
                 BiomeSelectors.all(),
                 (selection, context) -> {
                     for (RegistryKey<PlacedFeature> key : keys) {
                         try {
                             context.getGenerationSettings().removeFeature(key);
                         } catch (Throwable t) {
                             // Unknown placed feature. Skip it - never crash worldgen.
                             MISSING.add(key.getValue().toString());
                         }
                     }
                     reportOnce();
                 });

        UnbetaCore.LOG.info("Feature gate: attempting to strip {} placed feature(s) from all biomes.", keys.size());
    }

    private static void reportOnce() {
        if (reported || MISSING.isEmpty()) return;
        reported = true;
        UnbetaCore.LOG.warn("Feature gate: {} configured name(s) are not registered placed features "
                          + "and were skipped. Run /unbeta features to see the real IDs, then fix "
                          + "removedPlacedFeatures in config/unbeta/core.json:", MISSING.size());
        for (String m : MISSING) {
            UnbetaCore.LOG.warn("    not a placed feature: {}", m);
        }
    }
}
