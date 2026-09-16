package net.unbeta.content.clambox;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

public final class ClamboxRegistry {

    public static Block CLAMBOX_BLOCK;
    public static Item CLAMBOX_ITEM;
    public static Item PEARL_ITEM;
    public static BlockEntityType<ClamboxBlockEntity> CLAMBOX_BLOCK_ENTITY;
    public static net.minecraft.screen.ScreenHandlerType<ClamboxScreenHandler> CLAMBOX_SCREEN_HANDLER;

    private ClamboxRegistry() {}

    private static Identifier id(String path) {
        return new Identifier("unbeta-content", path);
    }

    public static void register() {
        CLAMBOX_BLOCK = Registry.register(Registries.BLOCK, id("clambox"),
                new ClamboxBlock(AbstractBlock.Settings.create()
                        .strength(1.5F)
                        .sounds(BlockSoundGroup.WOOL)
                        .nonOpaque()));

        CLAMBOX_ITEM = Registry.register(Registries.ITEM, id("clambox"),
                new BlockItem(CLAMBOX_BLOCK, new Item.Settings()));

        PEARL_ITEM = Registry.register(Registries.ITEM, id("unbeta_pearl"),
                new PearlItem(new Item.Settings().maxCount(1)));

        CLAMBOX_BLOCK_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE,
                id("clambox"),
                net.fabricmc.fabric.api.object.builder.v1.block.entity
                        .FabricBlockEntityTypeBuilder
                        .create(ClamboxBlockEntity::new, CLAMBOX_BLOCK).build());

        CLAMBOX_SCREEN_HANDLER =
                net.fabricmc.fabric.api.screenhandler.v1.ScreenHandlerRegistry.registerSimple(
                        id("clambox"), ClamboxScreenHandler::new);
    }
}
