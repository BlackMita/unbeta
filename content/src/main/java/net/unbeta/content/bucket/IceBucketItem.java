package net.unbeta.content.bucket;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsage;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/** Empty Ice Bucket: fills only from water or lava SOURCE blocks, and never takes them. */
public class IceBucketItem extends Item {

    public IceBucketItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        BlockHitResult hit = raycast(world, user, RaycastContext.FluidHandling.SOURCE_ONLY);
        if (hit.getType() != HitResult.Type.BLOCK) return TypedActionResult.pass(stack);
        FluidState fluid = world.getFluidState(hit.getBlockPos());
        if (!fluid.isStill()) return TypedActionResult.pass(stack); // source blocks only
        ItemStack filled;
        SoundEvent sound;
        if (fluid.isIn(FluidTags.WATER)) {
            filled = new ItemStack(IceBuckets.ICE_WATER_BUCKET);
            sound = SoundEvents.ITEM_BUCKET_FILL;
        } else if (fluid.isIn(FluidTags.LAVA)) {
            filled = new ItemStack(IceBuckets.ICE_LAVA_BUCKET);
            sound = SoundEvents.ITEM_BUCKET_FILL_LAVA;
        } else {
            return TypedActionResult.pass(stack);
        }
        IceFreeze.start(filled, world);
        user.playSound(sound, 1.0f, 1.0f);
        user.incrementStat(Stats.USED.getOrCreateStat(this));
        return TypedActionResult.success(ItemUsage.exchangeStack(stack, user, filled), world.isClient());
    }
}
