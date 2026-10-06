package net.unbeta.content.bucket;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.BoneMealItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * Wood bucket of water. On anything bone meal would work on, it waters it instead - the
 * bone-meal effect, no water spilled. Anywhere else it splashes a temporary source.
 * It never waterlogs blocks.
 */
public class WoodWaterBucketItem extends Item {

    public WoodWaterBucketItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        if (!BoneMealItem.useOnFertilizable(new ItemStack(Items.BONE_MEAL), world, pos)) return ActionResult.PASS;
        if (!world.isClient) {
            world.syncWorldEvent(1505, pos, 0); // bone meal particles
            PlayerEntity player = context.getPlayer();
            if (player != null) {
                player.playSound(SoundEvents.ITEM_BUCKET_EMPTY, 1.0f, 1.2f);
                if (!player.getAbilities().creativeMode) {
                    player.setStackInHand(context.getHand(), new ItemStack(BucketItems.WOOD_BUCKET));
                }
            }
        }
        return ActionResult.success(world.isClient);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        BlockHitResult hit = raycast(world, user, RaycastContext.FluidHandling.NONE);
        BlockPos target = TempFluids.target(world, hit);
        if (target == null) return TypedActionResult.pass(stack);
        if (world instanceof ServerWorld server) TempFluids.place(server, target, Fluids.WATER);
        user.playSound(SoundEvents.ITEM_BUCKET_EMPTY, 1.0f, 1.0f);
        user.incrementStat(Stats.USED.getOrCreateStat(this));
        return TypedActionResult.success(
                user.getAbilities().creativeMode ? stack : new ItemStack(BucketItems.WOOD_BUCKET), world.isClient());
    }
}
