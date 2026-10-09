package net.unbeta.content.bucket;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * Wood and copper buckets. Each holds water, lava or milk.
 *
 * <p>WOOD: fills from any water or lava, source or flowing, without taking it; pours only
 * TEMPORARY fluid; its lava burns through in 5 seconds. Water on a bone-meal target
 * fertilises it instead of pouring.
 * <p>COPPER: an iron bucket in every way, except its lava burns through in 15 seconds
 * (spilling real lava at your feet) and its milk leaves you poisoned.
 */
public final class BucketItems {

    public static final int WOOD_LAVA_TICKS = 20 * 5;
    public static final int COPPER_LAVA_TICKS = 20 * 15;

    public static Item WOOD_BUCKET, WOOD_WATER_BUCKET, WOOD_LAVA_BUCKET, WOOD_MILK_BUCKET;
    public static Item COPPER_BUCKET, COPPER_WATER_BUCKET, COPPER_LAVA_BUCKET, COPPER_MILK_BUCKET;

    private BucketItems() {}

    private static Item reg(String name, Item item) {
        return Registry.register(Registries.ITEM, new Identifier("unbeta-content", name), item);
    }

    private static Item.Settings one() {
        return new Item.Settings().maxCount(1);
    }

    public static void register() {
        WOOD_BUCKET = reg("wood_bucket", new WoodBucketItem(new Item.Settings().maxCount(16)));
        // An empty wood bucket burns like a wooden tool: 200 ticks, one item smelted (lit furnace only).
        net.fabricmc.fabric.api.registry.FuelRegistry.INSTANCE.add(WOOD_BUCKET, 200);
        WOOD_WATER_BUCKET = reg("wood_water_bucket", new WoodWaterBucketItem(one()));
        WOOD_LAVA_BUCKET = reg("wood_lava_bucket", new WoodLavaBucketItem(one()));
        WOOD_MILK_BUCKET = reg("wood_milk_bucket", new MilkVariantItem(one(), () -> WOOD_BUCKET, false));

        COPPER_BUCKET = reg("copper_bucket", new CopperBucketItem(new Item.Settings().maxCount(16)));
        COPPER_WATER_BUCKET = reg("copper_water_bucket", new CopperFilledBucketItem(Fluids.WATER, one()));
        COPPER_LAVA_BUCKET = reg("copper_lava_bucket",
                new CopperFilledBucketItem(Fluids.LAVA, one().recipeRemainder(COPPER_BUCKET)));
        COPPER_MILK_BUCKET = reg("copper_milk_bucket", new MilkVariantItem(one(), () -> COPPER_BUCKET, true));

        // Furnace fuel for every lava bucket lives in LavaFuel / FurnaceIgnitionMixin.

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
            entries.add(WOOD_BUCKET);
            entries.add(WOOD_WATER_BUCKET);
            entries.add(WOOD_LAVA_BUCKET);
            entries.add(WOOD_MILK_BUCKET);
            entries.add(COPPER_BUCKET);
            entries.add(COPPER_WATER_BUCKET);
            entries.add(COPPER_LAVA_BUCKET);
            entries.add(COPPER_MILK_BUCKET);
        });
        // Wood milk sits with the drinks as well, like vanilla milk.
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FOOD_AND_DRINK).register(entries -> entries.add(WOOD_MILK_BUCKET));
        TempFluids.register();
        IceBuckets.register();
        BucketExpiry.register();
    }
}
