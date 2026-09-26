package net.unbeta.content.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.unbeta.content.client.zombie.SulliedAtmosphere;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sullied air: while the camera is in a sullied chunk, the player's whole view takes a
 * translucent green cast - a screen overlay like the carved-pumpkin or powder-snow one.
 * Drawn first in the HUD pass, so it covers the world but sits beneath the hotbar and
 * crosshair. Eases in and out over two seconds (SulliedAtmosphere). Hidden with the HUD (F1),
 * like vanilla's own overlays.
 */
@Mixin(InGameHud.class)
public abstract class SulliedTintMixin {

    /** Tint colour (RGB) and its opacity at full strength (0..1). */
    private static final int TINT_RGB = 0x46962F;   // sickly green
    private static final float MAX_ALPHA = 0.17F;

    @Inject(method = "render", at = @At("HEAD"))
    private void unbeta_sulliedTint(DrawContext context, float tickDelta, CallbackInfo ci) {
        float s = SulliedAtmosphere.update(MinecraftClient.getInstance().gameRenderer.getCamera());
        // Fainter in the dark: over a night sky or a cave, the same green reads far stronger.
        float light = SulliedAtmosphere.updateLight(MinecraftClient.getInstance().gameRenderer.getCamera());
        if (s <= 0.0F) return;
        int alpha = Math.round(MAX_ALPHA * s * light * 255.0F);
        // Drawn far back in the HUD's depth. At normal depth it hid the hotbar's slot
        // background, which sits slightly behind the rest of the HUD: the depth test saw
        // the slots as being behind the tint and skipped them.
        context.getMatrices().push();
        context.getMatrices().translate(0.0F, 0.0F, -200.0F);
        context.fill(0, 0, context.getScaledWindowWidth(), context.getScaledWindowHeight(),
                (alpha << 24) | TINT_RGB);
        context.getMatrices().pop();
    }
}
