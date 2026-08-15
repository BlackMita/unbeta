package net.unbeta.content.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.render.RenderLayer;
import net.unbeta.content.UnbetaContent;

/**
 * Client setup for Phase 2 content. Assigns the obsidian fire block a cutout render
 * layer so its texture draws with transparency like vanilla fire, rather than as an
 * opaque cube.
 */
public final class UnbetaContentClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        net.unbeta.content.torch.TorchClient.register();
        net.unbeta.content.boomspore.BoomSporeClient.register();
        if (UnbetaContent.OBSIDIAN_FIRE != null) {
            BlockRenderLayerMap.INSTANCE.putBlock(
                    UnbetaContent.OBSIDIAN_FIRE, RenderLayer.getCutout());
        }
    }
}
