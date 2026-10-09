package net.unbeta.content.bucket;

import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.util.function.LongSupplier;

/**
 * Lava eats through wood and copper buckets. A lava-filled one stores the game time it
 * burns out at; the bar reads that against the clock, so it drains smoothly with no
 * per-second item updates. Checked every tick in an inventory and on the ground; in a
 * container (see BucketExpiry) an expired bucket simply vanishes.
 *
 * <p>Burning out destroys the bucket and spills its lava where it was - at the holder's
 * feet. Wood spills temporary lava, copper spills a real source.
 */
public final class BucketBurn {

    private static final String AT = "UnbetaBurnAt";
    private static final String LEN = "UnbetaBurnLen";
    public static final int BAR_COLOR = 0xFF6A00;

    /** Set by the client entrypoint; null on a dedicated server. */
    public static LongSupplier clientClock = null;

    private BucketBurn() {}

    public static void start(ItemStack stack, World world, int ticks) {
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putLong(AT, world.getTime() + ticks);
        nbt.putInt(LEN, ticks);
    }

    public static boolean burning(ItemStack stack) {
        return stack.hasNbt() && stack.getNbt().contains(AT);
    }

    public static boolean due(ItemStack stack, World world) {
        return burning(stack) && world.getTime() >= stack.getNbt().getLong(AT);
    }

    /** From inventoryTick: starts a timer on an untimed lava bucket (e.g. from the creative menu), or burns it out. */
    public static void inventoryTick(ItemStack stack, World world, Entity holder, int ticks) {
        if (world.isClient) return;
        if (!burning(stack)) {
            start(stack, world, ticks);
            return;
        }
        if (due(stack, world)) burnOut((ServerWorld) world, stack, holder.getBlockPos());
    }

    public static void burnOut(ServerWorld world, ItemStack stack, BlockPos at) {
        boolean wood = stack.isOf(BucketItems.WOOD_LAVA_BUCKET);
        stack.setCount(0); // the bucket is gone, wherever it was
        world.playSound(null, at, SoundEvents.ENTITY_GENERIC_BURN, SoundCategory.PLAYERS, 1.0f, 0.8f);
        world.playSound(null, at, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.7f, 1.0f);
        BlockPos spot = TempFluids.canHold(world.getBlockState(at)) ? at
                : TempFluids.canHold(world.getBlockState(at.up())) ? at.up() : null;
        if (spot == null) return;
        if (wood) {
            TempFluids.place(world, spot, Fluids.LAVA);
        } else {
            if (!world.getBlockState(spot).isAir() && world.getFluidState(spot).isEmpty()) world.breakBlock(spot, true);
            world.setBlockState(spot, Blocks.LAVA.getDefaultState(), Block.NOTIFY_ALL);
        }
    }

    public static int barStep(ItemStack stack) {
        if (!burning(stack) || clientClock == null) return 13;
        NbtCompound nbt = stack.getNbt();
        long left = nbt.getLong(AT) - clientClock.getAsLong();
        int len = Math.max(1, nbt.getInt(LEN));
        return MathHelper.clamp(Math.round(13.0f * left / len), 0, 13);
    }
}
