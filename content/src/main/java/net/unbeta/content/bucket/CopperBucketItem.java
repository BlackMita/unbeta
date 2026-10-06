package net.unbeta.content.bucket;

import net.minecraft.block.BlockState;
import net.minecraft.block.FluidDrainable;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsage;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.stat.Stats;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * Empty copper bucket: takes water and lava SOURCES exactly like the iron bucket (powder
 * snow and fish are left alone). Vanilla's drain hands back an IRON filled bucket, so the
 * result is swapped for the copper one.
 */
public class CopperBucketItem extends Item {

    public CopperBucketItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        BlockHitResult hit = raycast(world, user, RaycastContext.FluidHandling.SOURCE_ONLY);
        if (hit.getType() != HitResult.Type.BLOCK) return TypedActionResult.pass(stack);
        BlockPos pos = hit.getBlockPos();
        if (!world.canPlayerModifyAt(user, pos)) return TypedActionResult.fail(stack);

        BlockState state = world.getBlockState(pos);
        FluidState fluid = state.getFluidState();
        boolean water = fluid.isIn(FluidTags.WATER) && fluid.isStill();
        boolean lava = fluid.isIn(FluidTags.LAVA) && fluid.isStill();
        if (!(water || lava) || !(state.getBlock() instanceof FluidDrainable drainable)) {
            return TypedActionResult.pass(stack);
        }
        if (drainable.tryDrainFluid(world, pos, state).isEmpty()) return TypedActionResult.fail(stack);

        ItemStack filled = new ItemStack(water ? BucketItems.COPPER_WATER_BUCKET : BucketItems.COPPER_LAVA_BUCKET);
        if (lava) BucketBurn.start(filled, world, BucketItems.COPPER_LAVA_TICKS);
        drainable.getBucketFillSound().ifPresent(sound -> user.playSound(sound, 1.0f, 1.0f));
        user.incrementStat(Stats.USED.getOrCreateStat(this));
        return TypedActionResult.success(ItemUsage.exchangeStack(stack, user, filled), world.isClient());
    }
}
