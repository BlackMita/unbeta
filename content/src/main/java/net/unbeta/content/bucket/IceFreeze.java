package net.unbeta.content.bucket;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/**
 * A filled Ice Bucket freezes what it holds: 15 seconds after filling, water becomes an Ice
 * block, lava becomes Cobblestone and milk becomes Ice Cream - in an inventory, on the ground
 * or in a container. Its own timer keys, so it never mixes with lava burn-out.
 */
public final class IceFreeze {

    public static final int TICKS = 20 * 15;
    public static final int BAR_COLOR = 0x8FD8FF;
    private static final String AT = "UnbetaFreezeAt";
    private static final String LEN = "UnbetaFreezeLen";

    private IceFreeze() {}

    public static boolean isFilled(ItemStack s) {
        return s.isOf(IceBuckets.ICE_WATER_BUCKET) || s.isOf(IceBuckets.ICE_LAVA_BUCKET) || s.isOf(IceBuckets.ICE_MILK_BUCKET);
    }

    public static void start(ItemStack stack, World world) {
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putLong(AT, world.getTime() + TICKS);
        nbt.putInt(LEN, TICKS);
    }

    public static boolean freezing(ItemStack stack) {
        return stack.hasNbt() && stack.getNbt().contains(AT);
    }

    public static boolean due(ItemStack stack, World world) {
        return freezing(stack) && world.getTime() >= stack.getNbt().getLong(AT);
    }

    public static ItemStack result(ItemStack stack) {
        if (stack.isOf(IceBuckets.ICE_LAVA_BUCKET)) return new ItemStack(Items.COBBLESTONE);
        if (stack.isOf(IceBuckets.ICE_MILK_BUCKET)) return new ItemStack(IceBuckets.ICE_CREAM);
        return new ItemStack(Items.ICE);
    }

    public static void sound(ServerWorld world, BlockPos at, ItemStack from) {
        if (from.isOf(IceBuckets.ICE_LAVA_BUCKET)) {
            world.playSound(null, at, SoundEvents.BLOCK_LAVA_EXTINGUISH, SoundCategory.BLOCKS, 0.6f, 1.0f);
        } else {
            world.playSound(null, at, SoundEvents.BLOCK_GLASS_PLACE, SoundCategory.BLOCKS, 0.8f, 1.3f);
        }
    }

    /** From inventoryTick: starts an untimed one (e.g. from creative), or freezes a finished one in place. */
    public static void inventoryTick(ItemStack stack, World world, Entity holder) {
        if (world.isClient) return;
        if (!freezing(stack)) {
            start(stack, world);
            return;
        }
        if (!due(stack, world)) return;
        ItemStack done = result(stack);
        if (holder instanceof PlayerEntity player) {
            PlayerInventory inv = player.getInventory();
            for (int i = 0; i < inv.size(); i++) {
                if (inv.getStack(i) == stack) {
                    inv.setStack(i, done);
                    sound((ServerWorld) world, holder.getBlockPos(), stack);
                    return;
                }
            }
        } else if (holder instanceof LivingEntity living) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (living.getEquippedStack(slot) == stack) {
                    living.equipStack(slot, done);
                    sound((ServerWorld) world, holder.getBlockPos(), stack);
                    return;
                }
            }
        }
    }

    public static int barStep(ItemStack stack) {
        if (!freezing(stack) || BucketBurn.clientClock == null) return 13;
        NbtCompound nbt = stack.getNbt();
        long left = nbt.getLong(AT) - BucketBurn.clientClock.getAsLong();
        int len = Math.max(1, nbt.getInt(LEN));
        return MathHelper.clamp(Math.round(13.0f * left / len), 0, 13);
    }
}
