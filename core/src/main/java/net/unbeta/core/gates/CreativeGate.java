package net.unbeta.core.gates;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.unbeta.core.UnbetaCore;
import net.unbeta.core.api.ContentKind;
import net.unbeta.core.api.UnbetaApi;

/**
 * Removes gated items from every creative tab. Purely cosmetic - a gated item is
 * already uncraftable and undroppable; this just stops it appearing in the creative
 * inventory and (indirectly) in recipe viewers that read the creative index.
 *
 * <p>Controlled by config.hideFromCreative; when off, this gate does nothing so that
 * a developer can still spawn gated content for testing.
 */
public final class CreativeGate {

    private CreativeGate() {}

    public static void register() {
        ItemGroupEvents.MODIFY_ENTRIES_ALL.register((group, entries) -> {
            if (!UnbetaApi.isReady()) return;

            int[] removed = {0};
            entries.getDisplayStacks().removeIf(stack -> {
                Item item = stack.getItem();
                Identifier id = Registries.ITEM.getId(item);
                boolean gated = UnbetaApi.rules().isRemoved(ContentKind.ITEM, id);
                if (gated) removed[0]++;
                return gated;
            });
            entries.getSearchTabStacks().removeIf(stack ->
                    UnbetaApi.rules().isRemoved(ContentKind.ITEM, Registries.ITEM.getId(stack.getItem())));

            if (removed[0] > 0) {
                UnbetaCore.LOG.debug("Creative gate removed {} stack(s) from a tab.", removed[0]);
            }
        });
    }
}
