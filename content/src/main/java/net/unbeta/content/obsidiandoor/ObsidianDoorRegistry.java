package net.unbeta.content.obsidiandoor;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSetType;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class ObsidianDoorRegistry {

    public static Block OBSIDIAN_DOOR;
    public static Item OBSIDIAN_DOOR_ITEM;
    public static SoundEvent DOOR_OPEN;
    public static SoundEvent DOOR_CLOSE;
    public static BlockSetType OBSIDIAN_SET;

    private ObsidianDoorRegistry() {}

    private static Identifier id(String path) {
        return new Identifier("unbeta-content", path);
    }

    private static SoundEvent sound(String path) {
        Identifier i = id(path);
        return Registry.register(Registries.SOUND_EVENT, i, SoundEvent.of(i));
    }

    public static void register() {
        DOOR_OPEN  = sound("obsidian_door_open");
        DOOR_CLOSE = sound("obsidian_door_close");

        // Custom BlockSetType via Fabric's builder, wiring our door sounds in.
        OBSIDIAN_SET = new net.fabricmc.fabric.api.object.builder.v1.block.type.BlockSetTypeBuilder()
                .openableByHand(true)
                .soundGroup(BlockSoundGroup.STONE)
                .doorCloseSound(DOOR_CLOSE)
                .doorOpenSound(DOOR_OPEN)
                .register(id("obsidian"));

        OBSIDIAN_DOOR = Registry.register(Registries.BLOCK, id("obsidian_door"),
                new ObsidianDoorBlock(
                        AbstractBlock.Settings.create()
                                .mapColor(MapColor.BLACK)
                                .strength(25.0F, 1200.0F) // half obsidian's 50 hardness; full blast resist
                                .sounds(BlockSoundGroup.STONE)
                                .luminance(state -> 7)     // redstone-torch light, always
                                .nonOpaque()
                                .pistonBehavior(PistonBehavior.BLOCK),
                        OBSIDIAN_SET));

        OBSIDIAN_DOOR_ITEM = Registry.register(Registries.ITEM, id("obsidian_door"),
                new BlockItem(OBSIDIAN_DOOR, new Item.Settings()));
    }
}
