package net.unbeta.content.corruption;

import net.minecraft.item.FoodComponents;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * Seared Flesh: rotten flesh that went through fire. It eats exactly like rotten flesh
 * (same hunger, same chance of the Hunger effect), but searing purifies it, so it will
 * never carry the zombification risk that rotten flesh gets in Phase 5.
 *
 * <p>Sources: smelting rotten flesh, or killing a zombie, corrupted animal or Unlike Like
 * while it's on fire (see SearedFleshMixin).
 */
public final class SearedFlesh {

    public static Item ITEM;

    private SearedFlesh() {}

    public static void register() {
        ITEM = Registry.register(Registries.ITEM, new Identifier("unbeta-content", "seared_flesh"),
                new Item(new Item.Settings().food(FoodComponents.ROTTEN_FLESH)));
    }
}
