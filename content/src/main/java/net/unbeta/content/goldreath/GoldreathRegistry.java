package net.unbeta.content.goldreath;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/** The Goldreath: a winged golden Ghast that haunts the air around Skyholds. */
public final class GoldreathRegistry {

    public static EntityType<GoldreathEntity> GOLDREATH;
    public static EntityType<GoldreathBulletEntity> BULLET;

    private GoldreathRegistry() {}

    public static void register() {
        GOLDREATH = Registry.register(Registries.ENTITY_TYPE, new Identifier("unbeta-content", "goldreath"),
                FabricEntityTypeBuilder.<GoldreathEntity>create(SpawnGroup.MONSTER, GoldreathEntity::new)
                        .dimensions(EntityDimensions.fixed(3.0F, 3.0F))   // 3/4 of a Ghast
                        .fireImmune().trackRangeChunks(10).build());
        FabricDefaultAttributeRegistry.register(GOLDREATH, GoldreathEntity.createAttributes());
        BULLET = Registry.register(Registries.ENTITY_TYPE, new Identifier("unbeta-content", "goldreath_bullet"),
                FabricEntityTypeBuilder.<GoldreathBulletEntity>create(SpawnGroup.MISC, GoldreathBulletEntity::new)
                        .dimensions(EntityDimensions.fixed(0.5F, 0.5F))   // fatter than a shulker's
                        .trackRangeChunks(8).trackedUpdateRate(2).build());
        GoldreathSpawner.register();
    }
}
