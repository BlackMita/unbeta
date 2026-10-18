package net.unbeta.content.creative;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.AbstractPressurePlateBlock;
import net.minecraft.block.Block;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BoatItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MinecartItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.unbeta.core.api.UnbetaApi;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Creative menu tidying.
 * <ul>
 *   <li>Everywhere: only the cyan Hookshot, only the plain Cloud Boots.</li>
 *   <li>Functional Blocks: no paintings. Gains everything you place and then right-click to use
 *       or open - doors, trapdoors, fence gates, buttons, levers, beds, chests, furnaces... - plus
 *       boats and minecarts. Found by behaviour (the block has its own right-click action), so
 *       modded blocks are included automatically.</li>
 *   <li>Building Blocks: no doors, trapdoors, buttons or pressure plates (they live in Functional
 *       and Redstone).</li>
 *   <li>Redstone Blocks: gains the Obsidian Door.</li>
 * </ul>
 */
public final class CreativeTrims {

    /** Blocks with a right-click action that isn't "use it": harvesting, dust dots, eating, fuses. */
    private static final Set<String> NOT_FUNCTIONAL = Set.of(
            "minecraft:redstone", "minecraft:redstone_ore", "minecraft:deepslate_redstone_ore",
            "minecraft:pumpkin", "minecraft:cake", "minecraft:tnt", "minecraft:glow_berries",
            "minecraft:sweet_berries", "unbeta-content:bone_pile");

    /** Burnt blocks kept in the creative menu (everything else from Burnt is hidden). */
    private static final Set<String> BURNT_KEPT = Set.of(
            "burnt:burnt_log", "burnt:burnt_planks", "burnt:burnt_stairs",
            "burnt:burnt_slab", "burnt:burnt_fence", "burnt:burnt_fencegate",
            "burnt:burnt_pressure_plate", "burnt:burnt_oak_door",
            "burnt:burnt_oak_trapdoor", "burnt:burnt_ladder", "burnt:burnt_carpet");

    private static String onUseName;

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

    /** Would this item show up in creative at all? (Not gated, not trimmed.) */
    private static boolean shown(Item item) {
        ItemStack stack = new ItemStack(item);
        String id = Registries.ITEM.getId(item).toString();
        if (hidden(stack)) return false;
        if (id.startsWith("burnt:") && !BURNT_KEPT.contains(id)) return false;
        return !(UnbetaApi.isReady() && UnbetaApi.rules().isRemoved(item));
    }

    /** True if the block (or a parent below AbstractBlock) has its own right-click action. */
    private static boolean rightClickable(Block block) {
        if (onUseName == null) {
            onUseName = FabricLoader.getInstance().getMappingResolver().mapMethodName("intermediary",
                    "net.minecraft.class_4970", "method_9534",
                    "(Lnet/minecraft/class_2680;Lnet/minecraft/class_1937;Lnet/minecraft/class_2338;"
                            + "Lnet/minecraft/class_1657;Lnet/minecraft/class_1268;Lnet/minecraft/class_3965;)"
                            + "Lnet/minecraft/class_1269;");
        }
        for (Class<?> c = block.getClass(); c != null && c != AbstractBlock.class && c != Block.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(onUseName) && m.getParameterCount() == 6) return true;
            }
        }
        return false;
    }

    private static boolean functional(Item item) {
        if (NOT_FUNCTIONAL.contains(Registries.ITEM.getId(item).toString())) return false;
        if (item instanceof BoatItem || item instanceof MinecartItem) return true;
        return item instanceof BlockItem bi && rightClickable(bi.getBlock());
    }

    private static boolean doorLike(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem bi)) return false;
        Block b = bi.getBlock();
        return b instanceof DoorBlock || b instanceof TrapdoorBlock || b instanceof ButtonBlock
                || b instanceof AbstractPressurePlateBlock;
    }

    private static void addMissing(List<ItemStack> shown, java.util.function.Consumer<ItemStack> add, Iterable<Item> items) {
        Set<Item> present = new HashSet<>();
        for (ItemStack s : shown) present.add(s.getItem());
        for (Item item : items) {
            if (present.add(item)) add.accept(new ItemStack(item));
        }
    }

    public static void register() {
        ItemGroupEvents.MODIFY_ENTRIES_ALL.register((group, entries) -> {
            entries.getDisplayStacks().removeIf(CreativeTrims::hidden);
            entries.getSearchTabStacks().removeIf(CreativeTrims::hidden);

            RegistryKey<ItemGroup> key = Registries.ITEM_GROUP.getKey(group).orElse(null);
            if (key == null) return;
            if (key.equals(ItemGroups.FUNCTIONAL)) {
                entries.getDisplayStacks().removeIf(s -> Registries.ITEM.getId(s.getItem()).getPath().contains("painting"));
                List<Item> wanted = new java.util.ArrayList<>();
                for (Item item : Registries.ITEM) if (functional(item) && shown(item)) wanted.add(item);
                addMissing(entries.getDisplayStacks(), s -> entries.add(s, ItemGroup.StackVisibility.PARENT_TAB_ONLY), wanted);
            } else if (key.equals(ItemGroups.BUILDING_BLOCKS)) {
                entries.getDisplayStacks().removeIf(CreativeTrims::doorLike);
            } else if (key.equals(ItemGroups.REDSTONE)) {
                Item door = net.unbeta.content.obsidiandoor.ObsidianDoorRegistry.OBSIDIAN_DOOR.asItem();
                addMissing(entries.getDisplayStacks(), s -> entries.add(s, ItemGroup.StackVisibility.PARENT_TAB_ONLY), List.of(door));
            }
        });
    }
}
