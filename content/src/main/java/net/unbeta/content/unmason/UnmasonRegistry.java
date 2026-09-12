package net.unbeta.content.unmason;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class UnmasonRegistry {

    public static final Identifier ID = new Identifier("unbeta-content", "unmason");
    public static EntityType<UnmasonEntity> UNMASON;

    private UnmasonRegistry() {}

    public static void register() {
        UNMASON = Registry.register(Registries.ENTITY_TYPE, ID,
                FabricEntityTypeBuilder.<UnmasonEntity>create(SpawnGroup.MONSTER,
                        UnmasonEntity::new)
                        .dimensions(EntityDimensions.fixed(0.6F, 1.95F))
                        .build());
        FabricDefaultAttributeRegistry.register(UNMASON, UnmasonEntity.createAttributes());
    }
}
