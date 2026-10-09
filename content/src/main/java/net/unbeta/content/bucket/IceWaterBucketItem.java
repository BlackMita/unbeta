package net.unbeta.content.bucket;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
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

    /** Pours like the wood bucket, but hands back an empty Ice Bucket. */
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        TypedActionResult<ItemStack> result = super.use(world, user, hand);
        if (result.getValue().isOf(BucketItems.WOOD_BUCKET)) {
            return new TypedActionResult<>(result.getResult(), new ItemStack(IceBuckets.ICE_BUCKET));
        }
        return result;
    }
}
