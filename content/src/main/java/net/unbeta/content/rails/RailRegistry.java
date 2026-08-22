package net.unbeta.content.rails;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.PlacedFeature;

public final class RailRegistry {

    public static final String MOD_ID = "unbeta-content";
    public static final Identifier FEATURE_ID = new Identifier(MOD_ID, "rail_segment");

    public static final RegistryKey<PlacedFeature> PLACED_KEY =
            RegistryKey.of(RegistryKeys.PLACED_FEATURE, new Identifier(MOD_ID, "rail_segment"));

    public static Feature<DefaultFeatureConfig> RAIL_SEGMENT;

    private RailRegistry() {}

    public static void register() {
        RAIL_SEGMENT = Registry.register(Registries.FEATURE, FEATURE_ID,
                new RailSegmentFeature(DefaultFeatureConfig.CODEC));

        // Rails belong to the overworld only. Added at the surface-structures step so the
        // terrain heightmap is already final when surface trails query it.
        BiomeModifications.addFeature(
                BiomeSelectors.foundInOverworld(),
                GenerationStep.Feature.SURFACE_STRUCTURES,
                PLACED_KEY);
    }
}
