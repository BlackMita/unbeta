package net.unbeta.content.mixin;

import net.minecraft.client.render.entity.ArrowEntityRenderer;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.util.Identifier;
import net.unbeta.content.arrow.FireArrowAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Burned-out fire arrows render black, so they read as spent at a glance. */
@Mixin(ArrowEntityRenderer.class)
public abstract class CharredArrowTextureMixin {

    @Unique
    private static final Identifier UNBETA_CHARRED =
            new Identifier("unbeta-content", "textures/entity/projectiles/charred_arrow.png");

    @Inject(method = "getTexture(Lnet/minecraft/entity/projectile/ArrowEntity;)Lnet/minecraft/util/Identifier;",
            at = @At("HEAD"), cancellable = true)
    private void unbeta_charred(ArrowEntity arrow, CallbackInfoReturnable<Identifier> cir) {
        if (((FireArrowAccess) arrow).unbeta$isCharred()) cir.setReturnValue(UNBETA_CHARRED);
    }
}
