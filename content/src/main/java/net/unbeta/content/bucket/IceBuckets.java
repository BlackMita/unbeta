package net.unbeta.content.bucket;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.FoodComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * The Ice Bucket (three snow blocks in a V). Fills like a wood bucket - from any water or lava,
 * source or flowing, never taking it - and from cows. What it holds freezes after 15 seconds:
 * water into Ice, lava into Cobblestone, milk into Ice Cream. Every use consumes the bucket.
 */
public final class IceBuckets {

    public static Item ICE_BUCKET, ICE_WATER_BUCKET, ICE_LAVA_BUCKET, ICE_MILK_BUCKET, ICE_CREAM;

    private IceBuckets() {}

    private static Item reg(String name, Item item) {
        return Registry.register(Registries.ITEM, new Identifier("unbeta-content", name), item);
    }

    public static void register() {
        ICE_BUCKET = reg("ice_bucket", new IceBucketItem(new Item.Settings().maxCount(16)));
        ICE_WATER_BUCKET = reg("ice_water_bucket", new IceWaterBucketItem(new Item.Settings().maxCount(1)));
        ICE_LAVA_BUCKET = reg("ice_lava_bucket", new IceLavaBucketItem(new Item.Settings().maxCount(1)));
        ICE_MILK_BUCKET = reg("ice_milk_bucket", new IceMilkBucketItem(new Item.Settings().maxCount(1)));
        ICE_CREAM = reg("ice_cream", new IceCreamItem(new Item.Settings().maxCount(1).food(
                new FoodComponent.Builder().hunger(4).saturationModifier(0.3f).alwaysEdible().build())));

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
            entries.add(ICE_BUCKET);
            entries.add(ICE_WATER_BUCKET);
            entries.add(ICE_LAVA_BUCKET);
            entries.add(ICE_MILK_BUCKET);
        });
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FOOD_AND_DRINK).register(entries -> {
            entries.add(ICE_MILK_BUCKET);
            entries.add(ICE_CREAM);
        });
    }
}
