package net.unbeta.content.torch;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.item.VerticallyAttachableBlockItem;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;

/** The LIT torch item. Has a cosmetic burn bar that reads burnoutAt vs client time. */
public class UnbetaTorchItem extends VerticallyAttachableBlockItem {

    public UnbetaTorchItem(Block standing, Block wall, Settings settings, Direction direction) {
        super(standing, wall, settings, direction);
    }

    private static double remainingFraction(ItemStack stack) {
        long full = TorchItems.getFull(stack);
        long burnoutAt = TorchItems.getBurnoutAt(stack);
        long now = clientTime();
        if (full <= 0 || burnoutAt < 0 || now < 0) return 1.0;
        return MathHelper.clamp((double)(burnoutAt - now) / (double) full, 0.0, 1.0);
    }

    private static long clientTime() {
        try {
            var mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc != null && mc.world != null) return mc.world.getTime();
        } catch (Throwable ignored) {}
        return -1;
    }

    @Override public boolean isItemBarVisible(ItemStack stack) { return true; }

    @Override
    public int getItemBarStep(ItemStack stack) {
        return Math.round((float)(remainingFraction(stack) * 13.0));
    }

    @Override
    public int getItemBarColor(ItemStack stack) {
        return MathHelper.hsvToRgb((float)(remainingFraction(stack) * 0.33), 1.0F, 1.0F);
    }
}
