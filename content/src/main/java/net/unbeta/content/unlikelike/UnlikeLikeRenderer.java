package net.unbeta.content.unlikelike;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.SlimeEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class UnlikeLikeRenderer extends MobEntityRenderer<UnlikeLikeEntity, SlimeEntityModel<UnlikeLikeEntity>> {

    private static final Identifier TEXTURE =
            new Identifier("unbeta-content", "textures/entity/unlike_like.png");

    public UnlikeLikeRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new SlimeEntityModel<>(ctx.getPart(EntityModelLayers.SLIME)), 0.7F);
    }

    @Override
    public Identifier getTexture(UnlikeLikeEntity entity) {
        return TEXTURE;
    }
}
