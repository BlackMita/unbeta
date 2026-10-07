package net.unbeta.content.client.goldreath;

import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.unbeta.content.goldreath.GoldreathEntity;

/** The Ghast's own body and tentacles, plus a pair of feathered wings on its back. */
public class GoldreathModel extends SinglePartEntityModel<GoldreathEntity> {

    public static final EntityModelLayer LAYER = new EntityModelLayer(new Identifier("unbeta-content", "goldreath"), "main");

    private final ModelPart root;
    private final ModelPart[] tentacles = new ModelPart[9];
    private final ModelPart leftWing, rightWing;

    public GoldreathModel(ModelPart root) {
        this.root = root;
        for (int i = 0; i < tentacles.length; i++) tentacles[i] = root.getChild("tentacle" + i);
        this.leftWing = root.getChild("left_wing");
        this.rightWing = root.getChild("right_wing");
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData data = new ModelData();
        ModelPartData r = data.getRoot();
        r.addChild("body", ModelPartBuilder.create().uv(0, 0).cuboid(-8F, -8F, -8F, 16F, 16F, 16F),
                ModelTransform.pivot(0F, 17.6F, 0F));
        Random random = Random.create(1660L);          // the Ghast's own tentacle lengths
        for (int i = 0; i < 9; i++) {
            float f = (((float) (i % 3) - (float) (i / 3 % 2) * 0.5F + 0.25F) / 2.0F * 2.0F - 1.0F) * 5.0F;
            float g = ((float) (i / 3) / 2.0F * 2.0F - 1.0F) * 5.0F;
            int length = random.nextInt(7) + 8;
            r.addChild("tentacle" + i, ModelPartBuilder.create().uv(0, 0).cuboid(-1F, 0F, -1F, 2F, (float) length, 2F),
                    ModelTransform.pivot(f, 24.6F, g));
        }
        r.addChild("left_wing", ModelPartBuilder.create().uv(0, 32).cuboid(0F, -14F, 0F, 16F, 14F, 1F),
                ModelTransform.pivot(7F, 18F, 4F));
        r.addChild("right_wing", ModelPartBuilder.create().uv(0, 32).mirrored().cuboid(-16F, -14F, 0F, 16F, 14F, 1F),
                ModelTransform.pivot(-7F, 18F, 4F));
        return TexturedModelData.of(data, 64, 64);
    }

    @Override
    public ModelPart getPart() {
        return root;
    }

    @Override
    public void setAngles(GoldreathEntity entity, float limbAngle, float limbDistance, float animationProgress,
                          float headYaw, float headPitch) {
        for (int i = 0; i < tentacles.length; i++) {
            tentacles[i].pitch = 0.2F * MathHelper.sin(animationProgress * 0.3F + (float) i) + 0.4F;
        }
        float flap = MathHelper.sin(animationProgress * (entity.isShooting() ? 0.9F : 0.25F)) * 0.35F;
        leftWing.roll = -0.35F - flap;
        rightWing.roll = 0.35F + flap;
        leftWing.yaw = -0.25F;
        rightWing.yaw = 0.25F;
    }
}
