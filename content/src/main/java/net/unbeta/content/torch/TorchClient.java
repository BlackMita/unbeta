package net.unbeta.content.torch;

import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.render.RenderLayer;

/** Client setup: torch blocks render with a cutout layer (transparent background). */
public final class TorchClient {
    private TorchClient() {}
    public static void register() {
        BlockRenderLayerMap.INSTANCE.putBlock(UnbetaTorchRegistry.TORCH, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(UnbetaTorchRegistry.WALL_TORCH, RenderLayer.getCutout());

        // Item icon reflects lit/unlit via NBT (same trick as crossbow "charged").
        net.minecraft.client.item.ModelPredicateProviderRegistry.register(
                UnbetaTorchRegistry.TORCH_ITEM,
                new net.minecraft.util.Identifier("unbeta-content", "lit"),
                (stack, world, entity, seed) -> TorchItems.isLit(stack) ? 1.0F : 0.0F);
    }
}
