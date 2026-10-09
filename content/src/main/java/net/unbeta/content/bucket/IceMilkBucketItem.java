package net.unbeta.content.bucket;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/**
 * Milk in an Ice Bucket: drinks exactly like any milk (handing back an empty Ice Bucket), but
 * if it isn't drunk within 15 seconds it freezes into Ice Cream.
 */
public class IceMilkBucketItem extends MilkVariantItem {

    public IceMilkBucketItem(Settings settings) {
        super(settings, () -> IceBuckets.ICE_BUCKET, false);
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        IceFreeze.inventoryTick(stack, world, entity);
    }

    @Override
    public boolean isItemBarVisible(ItemStack stack) {
        return IceFreeze.freezing(stack);
    }

    @Override
    public int getItemBarStep(ItemStack stack) {
        return IceFreeze.barStep(stack);
    }

    @Override
    public int getItemBarColor(ItemStack stack) {
        return IceFreeze.BAR_COLOR;
    }
}
