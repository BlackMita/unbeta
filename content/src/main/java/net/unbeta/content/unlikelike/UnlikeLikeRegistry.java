package net.unbeta.content.unlikelike;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.attribute.DefaultAttributeRegistry;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.SpawnSettings;

public final class UnlikeLikeRegistry {

    public static final Identifier ID = new Identifier("unbeta-content", "unlike_like");

    public static EntityType<UnlikeLikeEntity> UNLIKE_LIKE;

    private UnlikeLikeRegistry() {}

    public static void register() {
        UNLIKE_LIKE = Registry.register(Registries.ENTITY_TYPE, ID,
                FabricEntityTypeBuilder.<UnlikeLikeEntity>create(SpawnGroup.MONSTER,
                        UnlikeLikeEntity::new)
                        .dimensions(EntityDimensions.fixed(1.5F, 2.0F))
                        .build());

        // Register attributes
        net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry
                .register(UNLIKE_LIKE, UnlikeLikeEntity.createAttributes());

        // Spawn rules
        SpawnRestriction.register(UNLIKE_LIKE,
                SpawnRestriction.Location.IN_WATER,
                Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                UnlikeLikeEntity::canSpawn);

        // Spawn in ocean and swamp biomes
        BiomeModifications.addSpawn(
                ctx -> ctx.getBiomeKey().getValue().getNamespace().equals("moderner_beta")
                        && (ctx.getBiomeKey().getValue().getPath().contains("ocean")
                            || ctx.getBiomeKey().getValue().getPath().contains("swamp")),
                SpawnGroup.MONSTER,
                UNLIKE_LIKE,
                3,   // weight (rare — lower than zombie's ~100)
                1,   // min group size
                2);  // max group size
    }
}
