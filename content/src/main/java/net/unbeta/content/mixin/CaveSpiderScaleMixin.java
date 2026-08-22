package net.unbeta.content.mixin;

import net.minecraft.client.render.entity.CaveSpiderEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.mob.CaveSpiderEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Renders cave spiders at 0.5x visual scale. Shadow stays at vanilla size. */
@Mixin(CaveSpiderEntityRenderer.class)
public class CaveSpiderScaleMixin {

    @Inject(method = "scale", at = @At("HEAD"), cancellable = true)
    private void unbeta_tinySpider(CaveSpiderEntity entity, MatrixStack matrices,
                                    float tickDelta, CallbackInfo ci) {
        matrices.scale(0.5F, 0.5F, 0.5F);
        ci.cancel();
    }
}
