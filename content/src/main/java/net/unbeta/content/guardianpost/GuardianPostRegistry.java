package net.unbeta.content.guardianpost;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/** The Guardian Post: the Darkhold's stationary, unkillable, experience-hungry watcher. */
public final class GuardianPostRegistry {

    public static EntityType<GuardianPostEntity> GUARDIAN_POST;

    private GuardianPostRegistry() {}

    public static void register() {
        GUARDIAN_POST = Registry.register(Registries.ENTITY_TYPE, new Identifier("unbeta-content", "guardian_post"),
                FabricEntityTypeBuilder.<GuardianPostEntity>create(SpawnGroup.MONSTER, GuardianPostEntity::new)
                        .dimensions(EntityDimensions.fixed(0.9F, 3.0F))
                        .fireImmune().trackRangeChunks(8).trackedUpdateRate(1).build());
        FabricDefaultAttributeRegistry.register(GUARDIAN_POST, GuardianPostEntity.createAttributes());
    }
}
