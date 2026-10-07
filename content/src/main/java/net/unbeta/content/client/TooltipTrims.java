package net.unbeta.content.client;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.unbeta.content.hookshot.CyanHookshot;

import java.util.Locale;

/**
 * Tooltip lines Unbeta doesn't want shown:
 * Cloud Boots' "unattainable / creative only" note, and the Hookshot's built-in Range upgrade.
 */
public final class TooltipTrims {

    private TooltipTrims() {}

    public static void register() {
        ItemTooltipCallback.EVENT.register((stack, context, lines) -> {
            if (Registries.ITEM.getId(stack.getItem()).getNamespace().equals("cloudboots")) {
                lines.removeIf(line -> {
                    String s = line.getString().toLowerCase(Locale.ROOT);
                    return s.contains("unattainable") || s.contains("unobtainable") || s.contains("creative");
                });
            }
            if (CyanHookshot.is(stack)) {
                String range = Text.translatable("hookshot.upgrade.hookshot.range").getString();
                lines.removeIf(line -> line.getString().trim().equals(range));
            }
        });
    }
}
