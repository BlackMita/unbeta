package net.unbeta.content.mixin;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * The frozen sheen: anything frozen (Ice Scream, or powder snow) is drawn tinted icy blue, the
 * tint fading as it thaws. Reads the vanilla freezing meter, which the game already sends to
 * every client.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class FrozenSheenMixin {

    @ModifyArgs(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/render/entity/model/EntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V"))
    private void unbeta_frozenSheen(Args args, LivingEntity entity, float yaw, float tickDelta,
                                    MatrixStack matrices, VertexConsumerProvider vertices, int light) {
        float s = entity.getFreezingScale();
        if (s <= 0.0F) return;
        args.set(4, args.<Float>get(4) * (1.0F - 0.50F * s));   // red
        args.set(5, args.<Float>get(5) * (1.0F - 0.22F * s));   // green
    }
}
