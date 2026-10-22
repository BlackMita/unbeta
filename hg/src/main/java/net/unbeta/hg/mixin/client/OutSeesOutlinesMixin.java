package net.unbeta.hg.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.unbeta.hg.client.OutClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** When you're out, everyone still in the round shows as an outline, even through walls. */
@Mixin(MinecraftClient.class)
public abstract class OutSeesOutlinesMixin {

    @Inject(method = "hasOutline", at = @At("HEAD"), cancellable = true)
    private void unbetaHg_outlineThoseStillIn(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        PlayerEntity me = ((MinecraftClient) (Object) this).player;
        if (me != null && entity instanceof PlayerEntity other && other != me
                && OutClient.isOut(me.getUuid()) && !OutClient.isOut(other.getUuid())) {
            cir.setReturnValue(true);
        }
    }
}
