package net.unbeta.content.bucket;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.BucketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * Copper bucket of water or lava. Pours REAL fluid exactly like the iron bucket (vanilla's
 * code, including waterlogging); vanilla hands back an empty IRON bucket, so that's swapped
 * for copper. The lava one burns through in 15 seconds.
 */
public class CopperFilledBucketItem extends BucketItem {

    private final int burnTicks;

    public CopperFilledBucketItem(Fluid fluid, Settings settings) {
        super(fluid, settings);
        this.burnTicks = fluid == Fluids.LAVA ? BucketItems.COPPER_LAVA_TICKS : 0;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        TypedActionResult<ItemStack> result = super.use(world, user, hand);
        if (result.getValue().isOf(Items.BUCKET)) {
            return new TypedActionResult<>(result.getResult(), new ItemStack(BucketItems.COPPER_BUCKET));
        }
        return result;
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (burnTicks > 0) BucketBurn.inventoryTick(stack, world, entity, burnTicks);
    }

    @Override
    public boolean isItemBarVisible(ItemStack stack) {
        return burnTicks > 0 && BucketBurn.burning(stack);
    }

    @Override
    public int getItemBarStep(ItemStack stack) {
        return BucketBurn.barStep(stack);
    }

    @Override
    public int getItemBarColor(ItemStack stack) {
        return BucketBurn.BAR_COLOR;
    }
}
