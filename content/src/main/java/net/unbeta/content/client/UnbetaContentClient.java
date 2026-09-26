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
        // Sullied chunk list from the server -> client, for surface spores.
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                net.unbeta.content.zombie.SulliedChunkSync.CHANNEL,
                (client, handler, buf, responseSender) -> {
                    int n = buf.readVarInt();
                    java.util.Set<Long> chunks = new java.util.HashSet<>();
                    for (int i = 0; i < n; i++) chunks.add(buf.readLong());
                    client.execute(() ->
                            net.unbeta.content.client.zombie.SulliedChunksClient.set(chunks));
                });
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> net.unbeta.content.client.zombie.SulliedChunksClient.clear());
        net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(
                net.unbeta.content.obsidiandoor.ObsidianDoorRegistry.OBSIDIAN_DOOR,
                net.minecraft.client.render.RenderLayer.getCutout());
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
                net.unbeta.content.clambox.ClamboxRegistry.PEARL_ENTITY,
                ctx -> new net.minecraft.client.render.entity.FlyingItemEntityRenderer<>(ctx));
        net.fabricmc.fabric.api.client.screenhandler.v1.ScreenRegistry.register(
                net.unbeta.content.clambox.ClamboxRegistry.CLAMBOX_SCREEN_HANDLER,
                net.unbeta.content.client.clambox.ClamboxScreen::new);
        net.minecraft.client.item.ModelPredicateProviderRegistry.register(
            net.unbeta.content.lockey.LockeyRegistry.LOCKEY,
            new net.minecraft.util.Identifier("unbeta-content", "bound"),
            (stack, world, entity, seed) ->
                net.unbeta.content.lockey.LockeyItem.isBound(stack) ? 1.0F : 0.0F);
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
                net.unbeta.content.unmason.UnmasonRegistry.UNMASON,
                net.unbeta.content.client.unmason.UnmasonRenderer::new);
        net.unbeta.content.torch.TorchClient.register();
        net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(
                net.unbeta.content.unlikelike.UnlikeLikeRenderer.LAYER,
                net.unbeta.content.unlikelike.UnlikeLikeModel::getTexturedModelData);
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
                net.unbeta.content.unlikelike.UnlikeLikeRegistry.UNLIKE_LIKE,
                net.unbeta.content.unlikelike.UnlikeLikeRenderer::new);
        net.unbeta.content.boomspore.BoomSporeClient.register();
        if (UnbetaContent.OBSIDIAN_FIRE != null) {
            BlockRenderLayerMap.INSTANCE.putBlock(
                    UnbetaContent.OBSIDIAN_FIRE, RenderLayer.getCutout());
        }
    }
}
