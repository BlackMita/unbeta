package net.unbeta.hg.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.unbeta.hg.DummyPlayer;
import net.unbeta.hg.Keeps;
import net.unbeta.hg.UnbetaHG;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Kept items are set aside before a dead player's inventory drops, and given back on respawn. */
@Mixin(PlayerEntity.class)
public abstract class KeepOnDeathMixin {

    @Inject(method = "dropInventory", at = @At("HEAD"))
    private void unbetaHg_stashKeeps(CallbackInfo ci) {
        if (UnbetaHG.ACTIVE && (Object) this instanceof ServerPlayerEntity p) Keeps.stashKept(p);
    }

    @Inject(method = "dropInventory", at = @At("TAIL"))
    private void unbetaHg_dummyKeeps(CallbackInfo ci) {
        // Dummies get up in place rather than through the Respawn button.
        if (UnbetaHG.ACTIVE && (Object) this instanceof DummyPlayer p) Keeps.restore(p);
    }
}
