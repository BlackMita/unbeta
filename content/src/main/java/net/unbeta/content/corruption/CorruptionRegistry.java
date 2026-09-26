package net.unbeta.content.corruption;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/** Corrupted livestock. Hitboxes match their vanilla bodies. */
public final class CorruptionRegistry {

    public static EntityType<ZombieCowEntity> ZOMBIE_COW;
    public static EntityType<ZombiePigEntity> ZOMBIE_PIG;
    public static EntityType<ZombieSheepEntity> ZOMBIE_SHEEP;
    public static EntityType<ZombieChickenEntity> ZOMBIE_CHICKEN;
    public static EntityType<RevenantEntity> REVENANT;

    private CorruptionRegistry() {}

    private static Identifier id(String path) {
        return new Identifier("unbeta-content", path);
    }

    public static void register() {
        ZOMBIE_COW = Registry.register(Registries.ENTITY_TYPE, id("zombie_cow"),
                FabricEntityTypeBuilder.<ZombieCowEntity>create(SpawnGroup.MONSTER, ZombieCowEntity::new)
                        .dimensions(EntityDimensions.fixed(0.9F, 1.4F)).build());
        FabricDefaultAttributeRegistry.register(ZOMBIE_COW, ZombieCowEntity.createAttributes());

        ZOMBIE_PIG = Registry.register(Registries.ENTITY_TYPE, id("zombie_pig"),
                FabricEntityTypeBuilder.<ZombiePigEntity>create(SpawnGroup.MONSTER, ZombiePigEntity::new)
                        .dimensions(EntityDimensions.fixed(0.9F, 0.9F)).build());
        FabricDefaultAttributeRegistry.register(ZOMBIE_PIG, ZombiePigEntity.createAttributes());

        ZOMBIE_SHEEP = Registry.register(Registries.ENTITY_TYPE, id("zombie_sheep"),
                FabricEntityTypeBuilder.<ZombieSheepEntity>create(SpawnGroup.MONSTER, ZombieSheepEntity::new)
                        .dimensions(EntityDimensions.fixed(0.9F, 1.3F)).build());
        FabricDefaultAttributeRegistry.register(ZOMBIE_SHEEP, ZombieSheepEntity.createAttributes());

        ZOMBIE_CHICKEN = Registry.register(Registries.ENTITY_TYPE, id("zombie_chicken"),
                FabricEntityTypeBuilder.<ZombieChickenEntity>create(SpawnGroup.MONSTER, ZombieChickenEntity::new)
                        .dimensions(EntityDimensions.fixed(0.4F, 0.7F)).build());
        FabricDefaultAttributeRegistry.register(ZOMBIE_CHICKEN, ZombieChickenEntity.createAttributes());

        REVENANT = Registry.register(Registries.ENTITY_TYPE, id("revenant"),
                FabricEntityTypeBuilder.<RevenantEntity>create(SpawnGroup.MONSTER, RevenantEntity::new)
                        .dimensions(EntityDimensions.fixed(0.6F, 1.95F)).build());
        FabricDefaultAttributeRegistry.register(REVENANT,
                net.minecraft.entity.mob.ZombieEntity.createZombieAttributes());
    }
}
