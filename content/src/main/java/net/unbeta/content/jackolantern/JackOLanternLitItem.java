package net.unbeta.content.jackolantern;

import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;

/** The lit jack o'lantern item with a cosmetic burn bar. */
public class JackOLanternLitItem extends BlockItem {

    public JackOLanternLitItem(Block block, Settings settings) {
        super(block, settings);
    }

    private static double remainingFraction(ItemStack stack) {
        long full = JackOLanternItems.getFull(stack);
        long burnoutAt = JackOLanternItems.getBurnoutAt(stack);
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
