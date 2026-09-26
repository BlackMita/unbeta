package net.unbeta.content.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.unbeta.content.corruption.Zombification;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While any gold armour is worn, the zombification countdown is frozen: this tick neither
 * counts down nor fires. Runs on both sides - armour is synced to the client - so the timer
 * on the player's screen stops too instead of drifting out of step with the server.
 */
@Mixin(StatusEffectInstance.class)
public abstract class ZombificationFreezeMixin {

    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void unbeta_freezeInGold(LivingEntity entity, Runnable overwriteCallback,
                                     CallbackInfoReturnable<Boolean> cir) {
        StatusEffectInstance self = (StatusEffectInstance)(Object)this;
        if (self.getEffectType() == Zombification.EFFECT && Zombification.isWearingGold(entity)) {
            cir.setReturnValue(true); // still active, not ticked
        }
    }
}
