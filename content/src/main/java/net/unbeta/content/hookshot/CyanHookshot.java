package net.unbeta.content.hookshot;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * Unbeta's Hookshot is the hookshot mod's Cyan Hookshot, permanently fitted with the mod's own
 * Range upgrade (what a chain adds at the smithing table) and shown simply as "Hookshot".
 */
public final class CyanHookshot {

    public static final Identifier ID = new Identifier("hookshot", "cyan_hookshot");

    private static Object range;

    private CyanHookshot() {}

    public static boolean is(ItemStack stack) {
        return !stack.isEmpty() && Registries.ITEM.getId(stack.getItem()).equals(ID);
    }

    /** The hookshot mod's Range upgrade, looked up once it exists (null until then, or without the mod). */
    public static Object rangeUpgrade() {
        if (range == null) {
            try {
                Object supplier = Class.forName("dev.cammiescorner.hookshot.registry.HookshotUpgrades")
                        .getField("RANGE").get(null);
                range = supplier.getClass().getMethod("get").invoke(supplier);
            } catch (Throwable ignored) {
            }
        }
        return range;
    }
}
