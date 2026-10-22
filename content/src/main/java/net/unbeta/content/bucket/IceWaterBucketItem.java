package net.unbeta.content.bucket;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/** Ice Bucket of water: waters bone-meal targets, pours temporary water - like the wood bucket - and freezes after 15 seconds instead of burning. */
public class IceWaterBucketItem extends WoodWaterBucketItem {

    public IceWaterBucketItem(Settings settings) {
        super(settings);
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

    /** Pours and waters like the wood bucket, but always hands back an empty Ice Bucket. */
    @Override
    protected net.minecraft.item.Item emptied() {
        return IceBuckets.ICE_BUCKET;
    }
}
