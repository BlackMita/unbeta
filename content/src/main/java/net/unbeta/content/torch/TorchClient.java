package net.unbeta.content.torch;

import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.render.RenderLayer;

public final class TorchClient {
    private TorchClient() {}
    public static void register() {
        BlockRenderLayerMap.INSTANCE.putBlock(UnbetaTorchRegistry.TORCH, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(UnbetaTorchRegistry.WALL_TORCH, RenderLayer.getCutout());
    }
}
