package net.unbeta.content.client.guardianpost;

import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.GuardianEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.mob.GuardianEntity;
import net.minecraft.util.Identifier;

/**
 * The Guardian Post: two blocks of obsidian with a tailless obsidian-skinned Guardian on top,
 * its one eye the colour of an experience orb. The beam is the Guardian renderer's own, drawn from the Post's eye 2.45 blocks up.
 */
public class GuardianPostRenderer extends GuardianEntityRenderer {

    private static final Identifier TEXTURE = new Identifier("unbeta-content", "textures/entity/guardian_post.png");

    public GuardianPostRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        try {
            this.getModel().getPart().getChild("head").getChild("tail0").visible = false;
        } catch (RuntimeException ignored) {
            // Model layout not as expected: keep the tail rather than crash.
        }
    }

    @Override
    public Identifier getTexture(GuardianEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(GuardianEntity entity, MatrixStack matrices, float amount) {
        matrices.translate(0.0F, -2.0F, 0.0F);   // (the model is drawn upside down here) up onto the post
    }

    @Override
    public void render(GuardianEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
        BlockRenderManager blocks = MinecraftClient.getInstance().getBlockRenderManager();
        matrices.push();
        matrices.translate(-0.5, 0.0, -0.5);
        for (int i = 0; i < 2; i++) {
            blocks.renderBlockAsEntity(Blocks.OBSIDIAN.getDefaultState(), matrices, vertexConsumers, light,
                    OverlayTexture.DEFAULT_UV);
            matrices.translate(0.0, 1.0, 0.0);
        }
        matrices.pop();
    }
}
