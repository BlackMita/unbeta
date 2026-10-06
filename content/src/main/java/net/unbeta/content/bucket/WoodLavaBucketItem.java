package net.unbeta.content.bucket;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/** Wood bucket of lava: pours temporary lava, and burns through in 5 seconds. */
public class WoodLavaBucketItem extends Item {

    public WoodLavaBucketItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        BlockHitResult hit = raycast(world, user, RaycastContext.FluidHandling.NONE);
        BlockPos target = TempFluids.target(world, hit);
        if (target == null) return TypedActionResult.pass(stack);
        if (world instanceof ServerWorld server) TempFluids.place(server, target, Fluids.LAVA);
        user.playSound(SoundEvents.ITEM_BUCKET_EMPTY_LAVA, 1.0f, 1.0f);
        user.incrementStat(Stats.USED.getOrCreateStat(this));
        return TypedActionResult.success(
                user.getAbilities().creativeMode ? stack : new ItemStack(BucketItems.WOOD_BUCKET), world.isClient());
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        BucketBurn.inventoryTick(stack, world, entity, BucketItems.WOOD_LAVA_TICKS);
    }

    @Override
    public boolean isItemBarVisible(ItemStack stack) {
        return BucketBurn.burning(stack);
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
