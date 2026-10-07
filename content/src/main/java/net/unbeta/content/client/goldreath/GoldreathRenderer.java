package net.unbeta.content.client.goldreath;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.unbeta.content.goldreath.GoldreathEntity;

/** 3/4 of a Ghast's size, always fully lit - it glows. */
public class GoldreathRenderer extends MobEntityRenderer<GoldreathEntity, GoldreathModel> {

    private static final Identifier TEXTURE = new Identifier("unbeta-content", "textures/entity/goldreath/goldreath.png");
    private static final Identifier SHOOTING = new Identifier("unbeta-content", "textures/entity/goldreath/goldreath_shooting.png");

    public GoldreathRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new GoldreathModel(ctx.getPart(GoldreathModel.LAYER)), 1.125F);
    }

    @Override
    public Identifier getTexture(GoldreathEntity entity) {
        return entity.isShooting() ? SHOOTING : TEXTURE;
    }

    @Override
    protected void scale(GoldreathEntity entity, MatrixStack matrices, float amount) {
        matrices.scale(3.375F, 3.375F, 3.375F);   // a Ghast's 4.5, times 3/4
    }

    @Override
    protected int getBlockLight(GoldreathEntity entity, BlockPos pos) {
        return 15;
    }
}
