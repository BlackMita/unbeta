package net.unbeta.content.unlikelike;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class UnlikeLikeRenderer extends MobEntityRenderer<UnlikeLikeEntity, UnlikeLikeModel> {

    public static final EntityModelLayer LAYER =
            new EntityModelLayer(new Identifier("unbeta-content", "unlike_like"), "main");

    private static final Identifier TEXTURE =
            new Identifier("unbeta-content", "textures/entity/unlike_like.png");

    public UnlikeLikeRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new UnlikeLikeModel(ctx.getPart(LAYER)), 0.9F);
    }

    @Override
    public Identifier getTexture(UnlikeLikeEntity entity) {
        return TEXTURE;
    }
}
