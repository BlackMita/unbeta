package net.unbeta.content.furnace;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.unbeta.content.bucket.BucketItems;

/**
 * Lava buckets as furnace fuel - the one fuel that lights an unlit furnace by itself.
 * Iron and copper burn as long as vanilla lava and hand back their empty bucket (recipe
 * remainder); a wood lava bucket burns twice as long as coal and leaves nothing.
 */
public final class LavaFuel {

    public static final int LAVA = 20000;
    public static final int WOOD_LAVA = 3200; // coal is 1600

    private LavaFuel() {}

    /** Burn time if this is a lava bucket of any kind, else 0. */
    public static int burnTime(ItemStack stack) {
        if (stack.isOf(Items.LAVA_BUCKET) || stack.isOf(BucketItems.COPPER_LAVA_BUCKET)) return LAVA;
        if (stack.isOf(BucketItems.WOOD_LAVA_BUCKET)) return WOOD_LAVA;
        return 0;
    }
}
