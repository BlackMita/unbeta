package net.unbeta.hg;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Item families for keeps: picking any member keeps the whole family, so a keep follows the
 * thing as the player thinks of it (a torch is a torch, lit or burnt out). Everything else is a
 * family of one. Shared by the server and the Keep screen.
 */
public final class Families {

    private Families() {}

    private static final List<List<String>> GROUPS = new ArrayList<>();
    static {
        GROUPS.add(List.of("unbeta-content:torch", "unbeta-content:lit_torch", "minecraft:torch"));
        GROUPS.add(List.of("unbeta-content:jack_o_lantern", "unbeta-content:lit_jack_o_lantern",
                "minecraft:carved_pumpkin", "minecraft:jack_o_lantern"));
        GROUPS.add(List.of("unbeta-content:wood_bucket", "unbeta-content:wood_water_bucket",
                "unbeta-content:wood_lava_bucket", "unbeta-content:wood_milk_bucket"));
        GROUPS.add(List.of("unbeta-content:copper_bucket", "unbeta-content:copper_water_bucket",
                "unbeta-content:copper_lava_bucket", "unbeta-content:copper_milk_bucket"));
        GROUPS.add(List.of("minecraft:bucket", "minecraft:water_bucket", "minecraft:lava_bucket",
                "minecraft:milk_bucket", "minecraft:powder_snow_bucket"));
        GROUPS.add(List.of("unbeta-content:ice_bucket", "unbeta-content:ice_water_bucket",
                "unbeta-content:ice_lava_bucket", "unbeta-content:ice_milk_bucket"));
        List<String> brushes = new ArrayList<>();
        brushes.add("unbeta-content:paintbrush");
        for (DyeColor c : DyeColor.values()) brushes.add("unbeta-content:" + c.getName() + "_paintbrush");
        GROUPS.add(brushes);
        List<String> beds = new ArrayList<>();
        beds.add("minecraft:red_bed");
        for (DyeColor c : DyeColor.values()) if (c != DyeColor.RED) beds.add("minecraft:" + c.getName() + "_bed");
        GROUPS.add(beds);
    }

    private static Map<Item, List<Item>> byItem;

    private static synchronized Map<Item, List<Item>> map() {
        if (byItem == null) {
            byItem = new HashMap<>();
            for (List<String> group : GROUPS) {
                List<Item> members = new ArrayList<>();
                for (String s : group) {
                    Identifier id = new Identifier(s);
                    if (Registries.ITEM.containsId(id)) members.add(Registries.ITEM.get(id));
                }
                for (Item i : members) byItem.put(i, members);
            }
        }
        return byItem;
    }

    /** The whole family of an item, the item itself first if it's on its own. */
    public static List<Item> of(Item item) {
        List<Item> members = map().get(item);
        return members != null ? members : List.of(item);
    }

    /** The item that stands for the family (its first member). */
    public static Item head(Item item) {
        return of(item).get(0);
    }
}
