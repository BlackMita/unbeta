package net.unbeta.content.torch;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.Item;
import net.minecraft.item.VerticallyAttachableBlockItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;

public final class UnbetaTorchRegistry {

    public static final String MOD_ID = "unbeta-content";

    public static UnbetaTorchBlock TORCH;
    public static UnbetaWallTorchBlock WALL_TORCH;
    public static Item TORCH_ITEM;         // unlit
    public static Item LIT_TORCH_ITEM;     // lit (has burn bar)
    public static BlockEntityType<UnbetaTorchBlockEntity> TORCH_BLOCK_ENTITY;

    private UnbetaTorchRegistry() {}

    private static Identifier id(String path) { return new Identifier(MOD_ID, path); }

    public static void register() {
        AbstractBlock.Settings base = AbstractBlock.Settings.create()
                .mapColor(MapColor.ORANGE).noCollision().strength(0.3F)
                .luminance(state -> state.get(UnbetaTorchBlock.LIT) ? 14 : 4)
                .sounds(BlockSoundGroup.WOOD).pistonBehavior(PistonBehavior.DESTROY);

        TORCH = Registry.register(Registries.BLOCK, id("torch"), new UnbetaTorchBlock(base));

        WALL_TORCH = Registry.register(Registries.BLOCK, id("wall_torch"),
                new UnbetaWallTorchBlock(AbstractBlock.Settings.create()
                        .mapColor(MapColor.ORANGE).noCollision().strength(0.3F)
                        .luminance(state -> state.get(UnbetaWallTorchBlock.LIT) ? 14 : 4)
                        .sounds(BlockSoundGroup.WOOD).pistonBehavior(PistonBehavior.DESTROY)
                        .dropsLike(TORCH)));

        TORCH_BLOCK_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE, id("torch"),
                FabricBlockEntityTypeBuilder.create(
                        UnbetaTorchBlockEntity::new, TORCH, WALL_TORCH).build());

        // Unlit torch: plain VerticallyAttachableBlockItem (no bar needed)
        TORCH_ITEM = Registry.register(Registries.ITEM, id("torch"),
                new VerticallyAttachableBlockItem(TORCH, WALL_TORCH,
                        new Item.Settings().maxCount(1), Direction.DOWN));

        // Lit torch: custom item with burn bar
        LIT_TORCH_ITEM = Registry.register(Registries.ITEM, id("lit_torch"),
                new UnbetaTorchItem(TORCH, WALL_TORCH,
                        new Item.Settings().maxCount(1), Direction.DOWN));
    }
}
