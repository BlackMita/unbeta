package net.unbeta.content.boomspore;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/**
 * The Boom Spore: a creeper-planted grenade. Throwable like a snowball.
 * 6-second silent fuse. Burns bar visible. Explodes with creeper strength.
 * NOT collectible — only obtainable from a creeper planting it in your inventory.
 */
public class BoomSporeItem extends Item {

    public static final String NBT_DETONATION_AT = "BoomSporeDetonationAt";
    public static final long FUSE_TICKS = 120L; // 6 seconds

    public BoomSporeItem(Settings settings) {
        super(settings);
    }

    /**
     * Stamp the fuse the moment this item enters any inventory for the first time.
     * This covers both creeper-planted spores AND creative-grabbed ones for testing.
     */
    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (!world.isClient && stack.isOf(BoomSporeRegistry.BOOM_SPORE_ITEM)) {
            NbtCompound nbt = stack.getOrCreateNbt();
            if (!nbt.contains(NBT_DETONATION_AT)) {
                nbt.putLong(NBT_DETONATION_AT, world.getTime() + FUSE_TICKS);
            }
        }
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        world.playSound(null, user.getX(), user.getY(), user.getZ(),
                SoundEvents.ENTITY_SNOWBALL_THROW, SoundCategory.NEUTRAL,
                0.5F, 0.4F / (world.getRandom().nextFloat() * 0.4F + 0.8F));
        if (!world.isClient) {
            BoomSporeEntity entity = new BoomSporeEntity(world, user);
            entity.setItem(stack);
            entity.setVelocity(user, user.getPitch(), user.getYaw(), 0.0F, 1.5F, 1.0F);
            if (stack.hasNbt() && stack.getNbt().contains(NBT_DETONATION_AT)) {
                entity.setDetonationAt(stack.getNbt().getLong(NBT_DETONATION_AT));
            }
            world.spawnEntity(entity);
        }
        user.incrementStat(Stats.USED.getOrCreateStat(this));
        if (!user.getAbilities().creativeMode) {
            stack.decrement(1);
        }
        return TypedActionResult.success(stack, world.isClient());
    }

    // ---- Burn bar (same pattern as lit torch, but drains in 6 seconds) ----

    @Override
    public boolean isItemBarVisible(ItemStack stack) {
        return isLive(stack);
    }

    @Override
    public int getItemBarStep(ItemStack stack) {
        double f = remainingFraction(stack);
        return Math.round((float)(f * 13.0));
    }

    @Override
    public int getItemBarColor(ItemStack stack) {
        double f = remainingFraction(stack);
        // Red the whole time — this is a bomb, not a torch
        return 0xFF0000; // always bright red
    }

    private double remainingFraction(ItemStack stack) {
        if (!isLive(stack)) return 1.0;
        long now = clientTime();
        if (now < 0) return 1.0;
        long detonationAt = getDetonationAt(stack);
        long left = detonationAt - now;
        return MathHelper.clamp((double) left / (double) FUSE_TICKS, 0.0, 1.0);
    }

    private static long clientTime() {
        try {
            var mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc != null && mc.world != null) return mc.world.getTime();
        } catch (Throwable ignored) {}
        return -1;
    }

    // ---- Static helpers ----

    public static ItemStack createLive(long now) {
        ItemStack stack = new ItemStack(BoomSporeRegistry.BOOM_SPORE_ITEM);
        stack.getOrCreateNbt().putLong(NBT_DETONATION_AT, now + FUSE_TICKS);
        return stack;
    }

    public static boolean isLive(ItemStack stack) {
        return !stack.isEmpty()
                && stack.isOf(BoomSporeRegistry.BOOM_SPORE_ITEM)
                && stack.hasNbt()
                && stack.getNbt().contains(NBT_DETONATION_AT);
    }

    public static long getDetonationAt(ItemStack stack) {
        return stack.hasNbt() ? stack.getNbt().getLong(NBT_DETONATION_AT) : Long.MAX_VALUE;
    }
}
