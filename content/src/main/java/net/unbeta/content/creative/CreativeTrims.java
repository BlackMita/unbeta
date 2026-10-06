package net.unbeta.content.creative;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

/**
 * Items kept out of the creative menu (every tab, and search) without being removed from
 * the game - they stay craftable and usable. Currently: every hookshot except the cyan one, and every Cloud Boots except the plain pair.
 */
public final class CreativeTrims {

    private CreativeTrims() {}

    static boolean hidden(ItemStack stack) {
        net.minecraft.util.Identifier id = Registries.ITEM.getId(stack.getItem());
        String path = id.getPath();
        // Hookshots: only the cyan one.
        if ((path.contains("hookshot") || path.contains("hook_shot")) && !path.contains("cyan")) return true;
        // Cloud Boots: only the plain pair.
        if (id.getNamespace().equals("cloudboots") && path.endsWith("boots") && !path.equals("cloud_boots")) return true;
        return false;
    }

    public static void register() {
        ItemGroupEvents.MODIFY_ENTRIES_ALL.register((group, entries) -> {
            entries.getDisplayStacks().removeIf(CreativeTrims::hidden);
            entries.getSearchTabStacks().removeIf(CreativeTrims::hidden);
        });
    }
}
