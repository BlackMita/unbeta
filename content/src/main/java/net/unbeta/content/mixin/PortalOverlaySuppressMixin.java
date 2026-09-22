package net.unbeta.content.mixin;

import net.unbeta.content.enderman.EndermanDreadState;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides the swirly nether-portal texture overlay specifically when EndermanDreadMixin
 * is the current source of nauseaIntensity, while leaving the FOV/view-wobble
 * distortion untouched - that lives in GameRenderer's projection code entirely
 * separately from this HUD render call, so cancelling this method has no effect on it.
 *
 * <p>A real portal or a real Nausea potion sets EndermanDreadState.active back to
 * false (see EndermanDreadMixin), so this never suppresses the overlay for genuine
 * vanilla portal use.
 */
@Mixin(InGameHud.class)
public abstract class PortalOverlaySuppressMixin {

    @Inject(method = "renderPortalOverlay", at = @At("HEAD"), cancellable = true)
    private void unbeta_suppressForEndermanDread(DrawContext context, float nauseaStrength,
                                                  CallbackInfo ci) {
        if (EndermanDreadState.active) {
            ci.cancel();
        }
    }
}
