package net.unbeta.content.burntchest;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * The Burnt Chest: a real chest (27 slots, joins into doubles with another Burnt Chest,
 * lockable and siegeable by Lockey) with a charcoal look. Chests can't catch fire, so the
 * only way to make one is to smelt a chest. Breaking it never drops the chest itself -
 * the contents still spill out as usual.
 */
public final class BurntChests {

    public static final Identifier ID = new Identifier("unbeta-content", "burnt_chest");

    public static BurntChestBlock BLOCK;
    public static Item ITEM;
    public static BlockEntityType<BurntChestBlockEntity> BLOCK_ENTITY;

    private BurntChests() {}

    public static void register() {
        BLOCK = Registry.register(Registries.BLOCK, ID, new BurntChestBlock(
                AbstractBlock.Settings.copy(Blocks.CHEST).mapColor(MapColor.BLACK).dropsNothing()));
        ITEM = Registry.register(Registries.ITEM, ID, new BlockItem(BLOCK, new Item.Settings()));
        BLOCK_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE, ID,
                BlockEntityType.Builder.create(BurntChestBlockEntity::new, BLOCK).build(null));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL)
                .register(entries -> entries.addAfter(Items.CHEST, ITEM));
    }
}
