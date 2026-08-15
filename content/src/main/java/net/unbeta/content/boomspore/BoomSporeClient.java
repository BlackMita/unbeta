package net.unbeta.content.boomspore;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;

public final class BoomSporeClient {
    private BoomSporeClient() {}

    public static void register() {
        EntityRendererRegistry.register(BoomSporeRegistry.BOOM_SPORE_ENTITY, FlyingItemEntityRenderer::new);
    }
}
