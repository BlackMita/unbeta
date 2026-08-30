package net.unbeta.content.unlikelike;

import net.minecraft.client.model.*;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;

public class UnlikeLikeModel extends EntityModel<UnlikeLikeEntity> {
    private final ModelPart bb_main;

    public UnlikeLikeModel(ModelPart root) {
        this.bb_main = root.getChild("bb_main");
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData modelData = new ModelData();
        ModelPartData modelPartData = modelData.getRoot();
        ModelPartData bb_main = modelPartData.addChild("bb_main",
            ModelPartBuilder.create()
                .uv(82, 70).cuboid(-8.0F, -4.0F, -8.0F, 16.0F, 4.0F, 16.0F, new Dilation(0.0F)),
            ModelTransform.pivot(0.0F, 24.0F, 0.0F));

        bb_main.addChild("cube_r1",
            ModelPartBuilder.create()
                .uv(0, 70).cuboid(-10.0F, -29.5F, -10.0F, 21.0F, 7.0F, 20.0F, new Dilation(0.0F)),
            ModelTransform.of(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.0436F));

        bb_main.addChild("cube_r2",
            ModelPartBuilder.create()
                .uv(0, 0).cuboid(-12.0F, -25.0F, -13.0F, 24.0F, 13.0F, 24.0F, new Dilation(0.0F)),
            ModelTransform.of(0.0F, 0.0F, 0.0F, -0.0436F, 0.0F, 0.0F));

        bb_main.addChild("cube_r3",
            ModelPartBuilder.create()
                .uv(0, 37).cuboid(-11.0F, -14.0F, -11.0F, 22.0F, 11.0F, 22.0F, new Dilation(0.0F)),
            ModelTransform.of(0.0F, 0.0F, 0.0F, 0.0436F, 0.0F, 0.0F));

        return TexturedModelData.of(modelData, 256, 256);
    }

    @Override
    public void setAngles(UnlikeLikeEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        // Phase D: squash/stretch animations go here
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumer vertexConsumer,
                       int light, int overlay, float red, float green, float blue, float alpha) {
        bb_main.render(matrices, vertexConsumer, light, overlay, red, green, blue, alpha);
    }
}
