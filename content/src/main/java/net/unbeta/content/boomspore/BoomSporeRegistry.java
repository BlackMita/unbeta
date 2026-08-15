package net.unbeta.content.boomspore;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class BoomSporeRegistry {

    public static final String MOD_ID = "unbeta-content";

    public static Item BOOM_SPORE_ITEM;
    public static EntityType<BoomSporeEntity> BOOM_SPORE_ENTITY;

    private BoomSporeRegistry() {}

    private static Identifier id(String path) { return new Identifier(MOD_ID, path); }

    public static void register() {
        // Entity type (must be registered BEFORE item, since item references it)
        BOOM_SPORE_ENTITY = Registry.register(Registries.ENTITY_TYPE, id("boom_spore"),
                FabricEntityTypeBuilder.<BoomSporeEntity>create(SpawnGroup.MISC, BoomSporeEntity::new)
                        .dimensions(EntityDimensions.fixed(0.25F, 0.25F))
                        .trackRangeBlocks(64)
                        .trackedUpdateRate(10)
                        .build());

        // Item
        BOOM_SPORE_ITEM = Registry.register(Registries.ITEM, id("boom_spore"),
                new BoomSporeItem(new Item.Settings().maxCount(1)));

        // Creative tab (for testing — remove later or keep in combat tab)
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT)
                .register(entries -> entries.add(BOOM_SPORE_ITEM));

        // Tick hook for fuse expiry
        BoomSporeTickHook.register();
    }
}
