package net.unbeta.content.mixin;

import net.minecraft.client.render.entity.CreeperEntityRenderer;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.util.Identifier;
import net.unbeta.content.boomspore.CreeperDataTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CreeperEntityRenderer.class)
public class CreeperRendererMixin {

    private static final Identifier HAPPY_TEXTURE =
            new Identifier("minecraft", "textures/entity/creeper/creeper_happy.png");

    @Inject(method = "getTexture", at = @At("RETURN"), cancellable = true)
    private void unbeta_happyTexture(CreeperEntity creeper,
                                     CallbackInfoReturnable<Identifier> cir) {
        if (creeper.getDataTracker().get(CreeperDataTracker.HAS_PLANTED)) {
            cir.setReturnValue(HAPPY_TEXTURE);
        }
    }
}
