package net.unbeta.content.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.CameraSubmersionType;
import net.minecraft.util.math.MathHelper;
import net.unbeta.content.client.skyhold.SkyholdFogClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pulls the fog in to ~24 blocks around a Skyhold. Water, lava and powder snow keep their own fog. */
@Mixin(BackgroundRenderer.class)
public abstract class SkyholdFogMixin {

    private static final float THICK_FOG_END = 24f;

    @Inject(method = "applyFog", at = @At("TAIL"))
    private static void unbeta_skyholdFog(Camera camera, BackgroundRenderer.FogType fogType, float viewDistance,
                                          boolean thickFog, float tickDelta, CallbackInfo ci) {
        float s = SkyholdFogClient.strength(tickDelta);
        if (s <= 0f || camera.getSubmersionType() != CameraSubmersionType.NONE) return;
        float start = RenderSystem.getShaderFogStart();
        float end = RenderSystem.getShaderFogEnd();
        RenderSystem.setShaderFogStart(MathHelper.lerp(s, start, 0f));
        RenderSystem.setShaderFogEnd(Math.min(end, MathHelper.lerp(s, end, THICK_FOG_END)));
    }
}
