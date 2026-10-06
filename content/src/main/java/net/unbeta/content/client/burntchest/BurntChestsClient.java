package net.unbeta.content.client.burntchest;

import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.render.block.entity.ChestBlockEntityRenderer;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.unbeta.content.burntchest.BurntChestBlockEntity;
import net.unbeta.content.burntchest.BurntChests;

/**
 * Draws Burnt Chests with vanilla's own chest renderer (lid animation and all); only the
 * texture differs, swapped in by BurntChestTextureMixin. The item is drawn the same way.
 */
public final class BurntChestsClient {

    private static final SpriteIdentifier SINGLE = sprite("burnt");
    private static final SpriteIdentifier LEFT = sprite("burnt_left");
    private static final SpriteIdentifier RIGHT = sprite("burnt_right");

    private BurntChestsClient() {}

    private static SpriteIdentifier sprite(String name) {
        return new SpriteIdentifier(TexturedRenderLayers.CHEST_ATLAS_TEXTURE,
                new Identifier("unbeta-content", "entity/chest/" + name));
    }

    public static SpriteIdentifier forType(ChestType type) {
        return switch (type) {
            case LEFT -> LEFT;
            case RIGHT -> RIGHT;
            default -> SINGLE;
        };
    }

    public static void register() {
        BlockEntityRendererFactories.register(BurntChests.BLOCK_ENTITY, ChestBlockEntityRenderer::new);
        BurntChestBlockEntity display = new BurntChestBlockEntity(BlockPos.ORIGIN, BurntChests.BLOCK.getDefaultState());
        BuiltinItemRendererRegistry.INSTANCE.register(BurntChests.ITEM,
                (stack, mode, matrices, consumers, light, overlay) ->
                        MinecraftClient.getInstance().getBlockEntityRenderDispatcher()
                                .renderEntity(display, matrices, consumers, light, overlay));
    }
}
