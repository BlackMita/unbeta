package net.unbeta.content.mixin;

import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.unbeta.content.bucket.BucketBurn;
import net.unbeta.content.bucket.BucketItems;
import net.unbeta.content.furnace.FurnaceIgnitionTracker;
import net.unbeta.content.furnace.LavaFuel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Wood and copper lava buckets can't wait forever in a furnace - their lava burns through.
 * The moment it does (5 s for wood, 15 s for copper), the lava pours into the fire instead of
 * being lost: a cold furnace lights, even with nothing to cook, and a burning one gets the
 * lava's burn time added on. Until then the bucket is ordinary fuel (used straight away if
 * there is something to cook). The copper bucket comes back empty; the wood bucket burns up.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class LavaBucketFurnaceMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private static void unbeta_lavaLightsItself(World world, BlockPos pos, BlockState state,
                                                AbstractFurnaceBlockEntity furnace, CallbackInfo ci) {
        if (world.isClient) return;
        ItemStack fuel = furnace.getStack(1);
        if (!fuel.isOf(BucketItems.WOOD_LAVA_BUCKET) && !fuel.isOf(BucketItems.COPPER_LAVA_BUCKET)) return;

        if (!BucketBurn.burning(fuel)) {   // e.g. straight from the creative menu: start its clock
            BucketBurn.start(fuel, world, fuel.isOf(BucketItems.WOOD_LAVA_BUCKET)
                    ? BucketItems.WOOD_LAVA_TICKS : BucketItems.COPPER_LAVA_TICKS);
        }
        if (!BucketBurn.due(fuel, world)) return;   // its lava hasn't burned through yet

        FurnaceTimesAccessor times = (FurnaceTimesAccessor) furnace;
        int left = times.unbeta_getBurnTime();

        int total = left + LavaFuel.burnTime(fuel);
        times.unbeta_setBurnTime(total);
        times.unbeta_setFuelTime(total);
        Item remainder = fuel.getItem().getRecipeRemainder();
        furnace.setStack(1, remainder == null ? ItemStack.EMPTY : new ItemStack(remainder));
        FurnaceIgnitionTracker.ignite(pos);
        if (!state.get(AbstractFurnaceBlock.LIT)) {
            world.setBlockState(pos, state.with(AbstractFurnaceBlock.LIT, true), Block.NOTIFY_ALL);
        }
        world.playSound(null, pos, SoundEvents.ITEM_BUCKET_EMPTY_LAVA, SoundCategory.BLOCKS, 0.8F, 1.0F);
        furnace.markDirty();
    }
}
