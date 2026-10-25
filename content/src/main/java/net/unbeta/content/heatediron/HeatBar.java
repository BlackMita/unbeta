package net.unbeta.content.heatediron;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

/** How much heat an item has left, for its bar (worked out on the player's game from world time). */
final class HeatBar {

    private HeatBar() {}

    static float fraction(ItemStack stack) {
        NbtCompound be = stack.getSubNbt("BlockEntityTag");
        if (be == null || !be.contains(HeatedIron.KEY)) return 1.0F;
        long now;
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.world == null) return 1.0F;
            now = mc.world.getTime();
        } catch (Throwable t) {          // on a dedicated server there's no client
            return 1.0F;
        }
        return Math.max(0F, Math.min(1F, (be.getLong(HeatedIron.KEY) - now) / (float) HeatedIron.MAX_HEAT));
    }
}
