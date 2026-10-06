package net.unbeta.content.copper;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/** Copper nuggets (9 to an ingot) and copper wire (3 nuggets, diagonal). */
public final class CopperItems {

    public static Item COPPER_NUGGET;
    public static Item COPPER_WIRE;

    private CopperItems() {}

    public static void register() {
        COPPER_NUGGET = Registry.register(Registries.ITEM,
                new Identifier("unbeta-content", "copper_nugget"), new Item(new Item.Settings()));
        COPPER_WIRE = Registry.register(Registries.ITEM,
                new Identifier("unbeta-content", "copper_wire"), new Item(new Item.Settings()));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(entries -> {
            entries.add(COPPER_NUGGET);
            entries.add(COPPER_WIRE);
        });
    }
}
