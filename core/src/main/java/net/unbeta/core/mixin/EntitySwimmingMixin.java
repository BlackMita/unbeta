package net.unbeta.core.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.unbeta.core.api.UnbetaApi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Beta 1.8 has no swimming pose - you bob upright in water. Swimming (and the
 * sprint-swim dash) arrived in 1.13.
 *
 * <p>Cancels {@code setSwimming(true)} for players, so the swim state never engages.
 * Non-player entities (dolphins, fish) are untouched - they are gated out anyway,
 * and leaving their logic alone avoids surprises for modded aquatic mobs.
 */
@Mixin(Entity.class)
public abstract class EntitySwimmingMixin {

    @Inject(method = "setSwimming", at = @At("HEAD"), cancellable = true)
    private void unbeta$noSwimming(boolean swimming, CallbackInfo ci) {
        if (!swimming) return;
        if (!UnbetaApi.isReady()) return;
        if (!((Object) this instanceof PlayerEntity)) return;
        if (UnbetaApi.rules().isSystemDisabled("swimming")) {
            ci.cancel();
        }
    }
}
