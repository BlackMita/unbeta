package net.unbeta.content.torch;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

/**
 * Registers the Unbeta torch: standing block, wall block, block entity, and item.
 *
 * <p>Design choices:
 * <ul>
 *   <li>Both blocks share one block-entity type (they share burn-life logic).</li>
 *   <li>Luminance is state-dependent via a lambda: 14 when LIT, else 0.</li>
 *   <li><b>Mined-not-slapped:</b> a small non-zero hardness (0.3) means breaking takes a
 *       brief moment rather than an instant hand-slap - so you can hit/right-click a
 *       placed torch without knocking it off.</li>
 *   <li>The item is <b>unstackable</b> (maxCount 1) so each torch carries its own state.</li>
 * </ul>
 */
public final class UnbetaTorchRegistry {

    public static final String MOD_ID = "unbeta-content";

    public static UnbetaTorchBlock TORCH;
    public static UnbetaWallTorchBlock WALL_TORCH;
    public static Item TORCH_ITEM;
    public static BlockEntityType<UnbetaTorchBlockEntity> TORCH_BLOCK_ENTITY;

    private UnbetaTorchRegistry() {}

    private static Identifier id(String path) { return new Identifier(MOD_ID, path); }

    public static void register() {
        AbstractBlock.Settings base = AbstractBlock.Settings.create()
                .mapColor(MapColor.ORANGE)
                .noCollision()
                .strength(0.3F)                 // mined-not-slapped: brief break time
                .luminance(state -> state.get(UnbetaTorchBlock.LIT) ? 14 : 0)
                .sounds(BlockSoundGroup.WOOD)
                .pistonBehavior(PistonBehavior.DESTROY);

        TORCH = Registry.register(Registries.BLOCK, id("torch"),
                new UnbetaTorchBlock(base));

        WALL_TORCH = Registry.register(Registries.BLOCK, id("wall_torch"),
                new UnbetaWallTorchBlock(AbstractBlock.Settings.create()
                        .mapColor(MapColor.ORANGE)
                        .noCollision()
                        .strength(0.3F)
                        .luminance(state -> state.get(UnbetaWallTorchBlock.LIT) ? 14 : 0)
                        .sounds(BlockSoundGroup.WOOD)
                        .pistonBehavior(PistonBehavior.DESTROY)
                        .dropsLike(TORCH)));

        // One block entity type for both blocks.
        TORCH_BLOCK_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE, id("torch"),
                FabricBlockEntityTypeBuilder.create(
                        UnbetaTorchBlockEntity::new, TORCH, WALL_TORCH).build());

        // Item: unstackable, places the standing torch but auto-uses wall variant on walls
        // (vanilla WallStandingBlockItem behaviour).
        TORCH_ITEM = Registry.register(Registries.ITEM, id("torch"),
                new UnbetaTorchItem(TORCH, WALL_TORCH,
                        new Item.Settings().maxCount(1), net.minecraft.util.math.Direction.DOWN));
    }
}
