package net.unbeta.hg.mixin.client;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.unbeta.hg.client.OutClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Out players are drawn partly see-through, like ghosts. */
@Mixin(LivingEntityRenderer.class)
public abstract class GhostRenderMixin {

    @Unique
    private static boolean unbetaHg_ghost;

    @Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"))
    private void unbetaHg_whoIsThis(LivingEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                                    VertexConsumerProvider vertices, int light, CallbackInfo ci) {
        unbetaHg_ghost = entity instanceof PlayerEntity && OutClient.isOut(entity.getUuid());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Inject(method = "getRenderLayer", at = @At("HEAD"), cancellable = true)
    private void unbetaHg_ghostLayer(LivingEntity entity, boolean showBody, boolean translucent, boolean showOutline,
                                     CallbackInfoReturnable<RenderLayer> cir) {
        if (unbetaHg_ghost && showBody) {
            cir.setReturnValue(RenderLayer.getItemEntityTranslucentCull(((LivingEntityRenderer) (Object) this).getTexture(entity)));
        }
    }

    @ModifyArg(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/model/EntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V"),
            index = 7)
    private float unbetaHg_ghostAlpha(float alpha) {
        return unbetaHg_ghost ? 0.4F : alpha;
    }
}
