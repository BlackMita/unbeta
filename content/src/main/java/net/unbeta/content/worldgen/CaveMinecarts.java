package net.unbeta.content.worldgen;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.GenerationStep;

/** Registers the cave minecart and adds it to every overworld biome's underground decoration. */
public final class CaveMinecarts {

    public static final Identifier ID = new Identifier("unbeta-content", "cave_minecart");

    private CaveMinecarts() {}

    public static void register() {
        Registry.register(Registries.FEATURE, ID, new CaveMinecartFeature());
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(),
                GenerationStep.Feature.UNDERGROUND_DECORATION,
                RegistryKey.of(RegistryKeys.PLACED_FEATURE, ID));
    }
}
