package net.unbeta.content.torch;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.item.VerticallyAttachableBlockItem;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;

/**
 * The torch item, extended to show a COSMETIC burn-life bar when lit. Display-only; never
 * destroys the torch. Reads the absolute burnout timestamp and compares to the CLIENT
 * world time at render, so there are zero NBT writes (no item "dip").
 */
public class UnbetaTorchItem extends VerticallyAttachableBlockItem {

    public UnbetaTorchItem(Block standing, Block wall, Settings settings, Direction direction) {
        super(standing, wall, settings, direction);
    }

    private static double remainingFraction(ItemStack stack) {
        if (!TorchItems.isLit(stack)) return -1;
        var nbt = stack.getNbt();
        if (nbt == null) return -1;
        long total = nbt.contains(TorchItems.NBT_FULL)
                ? nbt.getLong(TorchItems.NBT_FULL)
                : UnbetaTorchBlockEntity.DEFAULT_BURN_TICKS;
        if (total <= 0) return -1;
        long now = clientTime();
        if (!nbt.contains(TorchItems.NBT_BURNOUT_AT) || now < 0) return 1.0;
        long left = nbt.getLong(TorchItems.NBT_BURNOUT_AT) - now;
        return MathHelper.clamp((double) left / (double) total, 0.0, 1.0);
    }

    private static long clientTime() {
        try {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc != null && mc.world != null) return mc.world.getTime();
        } catch (Throwable ignored) {}
        return -1;
    }

    @Override
    public boolean isItemBarVisible(ItemStack stack) {
        return remainingFraction(stack) >= 0.0;
    }

    @Override
    public int getItemBarStep(ItemStack stack) {
        double f = remainingFraction(stack);
        if (f < 0) return 0;
        return Math.round((float) (f * 13.0));
    }

    @Override
    public int getItemBarColor(ItemStack stack) {
        double f = Math.max(0.0, remainingFraction(stack));
        float hue = (float) (f * 0.33);
        return MathHelper.hsvToRgb(hue, 1.0F, 1.0F);
    }

}
