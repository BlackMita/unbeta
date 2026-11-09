package net.unbeta.content.mixin;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.BoatEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.entity.vehicle.ChestBoatEntity;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import net.unbeta.content.dye.PaintedBoat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** A painted boat is drawn with its paint colour's wood. */
@Mixin(BoatEntityRenderer.class)
public abstract class BoatPaintTextureMixin {

    @ModifyVariable(method = "render(Lnet/minecraft/entity/vehicle/BoatEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("STORE"), ordinal = 0)
    private Identifier unbeta_paintedTexture(Identifier texture, BoatEntity boat, float yaw, float tickDelta,
                                             MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        int c = ((PaintedBoat) boat).unbeta_paint();
        if (c < 0 || c > 15) return texture;
        String kind = boat instanceof ChestBoatEntity ? "chest_boat" : "boat";
        return new Identifier("unbeta-content", "textures/entity/" + kind + "/" + DyeColor.byId(c).getName() + ".png");
    }
}
