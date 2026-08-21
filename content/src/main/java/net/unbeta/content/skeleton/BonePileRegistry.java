package net.unbeta.content.skeleton;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

public final class BonePileRegistry {

    public static final String MOD_ID = "unbeta-content";

    public static BonePileBlock BONE_PILE_BLOCK;
    public static Item BONE_PILE_ITEM;
    public static BlockEntityType<BonePileBlockEntity> BONE_PILE_BLOCK_ENTITY;

    /** UUIDs of skeletons that are holding non-weapon items (harmless mode). */
    public static final java.util.Set<java.util.UUID> HARMLESS_SKELETONS =
            java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    private BonePileRegistry() {}

    private static Identifier id(String path) { return new Identifier(MOD_ID, path); }

    public static void register() {
        // Block: fixed hardness 1.5 (~3 seconds with hand), no tool requirement,
        // blast resistance 0 (explosions destroy it), bone sound
        BONE_PILE_BLOCK = Registry.register(Registries.BLOCK, id("bone_pile"),
                new BonePileBlock(AbstractBlock.Settings.create()
                        .mapColor(MapColor.WHITE)
                        .hardness(1.5F)
                        .resistance(0.0F)
                        .sounds(BlockSoundGroup.BONE)
                        .nonOpaque()));

        // Block entity
        BONE_PILE_BLOCK_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE, id("bone_pile"),
                FabricBlockEntityTypeBuilder.create(BonePileBlockEntity::new, BONE_PILE_BLOCK).build());

        // Item (for creative testing, not normally obtainable)
        BONE_PILE_ITEM = Registry.register(Registries.ITEM, id("bone_pile"),
                new BlockItem(BONE_PILE_BLOCK, new Item.Settings()));
    }
}
