package net.unbeta.content.unlikelike;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.SpawnSettings;

public final class UnlikeLikeRegistry {

    public static final Identifier ID = new Identifier("unbeta-content", "unlike_like");
    public static EntityType<UnlikeLikeEntity> UNLIKE_LIKE;

    private UnlikeLikeRegistry() {}

    public static void register() {
        UNLIKE_LIKE = Registry.register(Registries.ENTITY_TYPE, ID,
                FabricEntityTypeBuilder.<UnlikeLikeEntity>create(SpawnGroup.WATER_CREATURE,
                        UnlikeLikeEntity::new)
                        .dimensions(EntityDimensions.fixed(1.5F, 2.0F))
                        .build());

        FabricDefaultAttributeRegistry.register(UNLIKE_LIKE, UnlikeLikeEntity.createAttributes());

        // Spawn restriction: in water, like squids
        net.minecraft.entity.SpawnRestriction.register(UNLIKE_LIKE,
                net.minecraft.entity.SpawnRestriction.Location.IN_WATER,
                net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                UnlikeLikeEntity::canSpawn);

        // Spawn in all biomes as a water creature alongside squids.
        // Squids have weight 10; weight 3 here = ~23% of water creature spawns = ~1 in 4.
        BiomeModifications.addSpawn(
                ctx -> true, // all biomes
                SpawnGroup.WATER_CREATURE,
                UNLIKE_LIKE,
                3, 1, 2);
    }
}
