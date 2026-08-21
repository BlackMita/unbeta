package net.unbeta.content.jackolantern;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;

public final class JackOLanternRegistry {

    public static final String MOD_ID = "unbeta-content";

    public static UnbetaJackOLanternBlock    UNLIT_BLOCK;
    public static UnbetaLitJackOLanternBlock LIT_BLOCK;
    public static Item UNLIT_ITEM;
    public static Item LIT_ITEM;
    public static BlockEntityType<JackOLanternBlockEntity> BLOCK_ENTITY;

    private JackOLanternRegistry() {}

    private static Identifier id(String path) { return new Identifier(MOD_ID, path); }

    public static void register() {
        AbstractBlock.Settings base = AbstractBlock.Settings.create()
                .mapColor(MapColor.ORANGE)
                .strength(1.0F)
                .sounds(BlockSoundGroup.WOOD);

        UNLIT_BLOCK = Registry.register(Registries.BLOCK, id("jack_o_lantern"),
                new UnbetaJackOLanternBlock(base.luminance(state -> state.get(JackOLanternLogic.LIT) ? 15 : 0)));

        LIT_BLOCK = Registry.register(Registries.BLOCK, id("lit_jack_o_lantern"),
                new UnbetaLitJackOLanternBlock(
                        AbstractBlock.Settings.create()
                                .mapColor(MapColor.ORANGE)
                                .strength(1.0F)
                                .sounds(BlockSoundGroup.WOOD)
                                .luminance(state -> state.get(JackOLanternLogic.LIT) ? 15 : 0)));

        BLOCK_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE, id("jack_o_lantern"),
                FabricBlockEntityTypeBuilder.create(JackOLanternBlockEntity::new,
                        UNLIT_BLOCK, LIT_BLOCK).build());

        UNLIT_ITEM = Registry.register(Registries.ITEM, id("jack_o_lantern"),
                new JackOLanternUnlitItem(UNLIT_BLOCK, new Item.Settings().maxCount(16)));

        LIT_ITEM = Registry.register(Registries.ITEM, id("lit_jack_o_lantern"),
                new JackOLanternLitItem(LIT_BLOCK, new Item.Settings().maxCount(1)));

        // Creative tabs
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> {
            entries.add(UNLIT_ITEM);
            entries.add(LIT_ITEM);
        });
    }
}
