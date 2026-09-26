package net.unbeta.content.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.unbeta.content.corruption.Zombification;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Clearing all effects (milk, /effect clear, a totem) leaves zombification in place: it's
 * set aside before the clear and put back after, remaining time intact. Only a golden apple
 * cures it.
 */
@Mixin(LivingEntity.class)
public abstract class ZombificationMilkMixin {

    @Unique private StatusEffectInstance unbeta_keptZombification;

    @Inject(method = "clearStatusEffects", at = @At("HEAD"))
    private void unbeta_setAside(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (self.getWorld().isClient) return;
        StatusEffectInstance z = self.getStatusEffect(Zombification.EFFECT);
        this.unbeta_keptZombification = z == null ? null : new StatusEffectInstance(z);
    }

    @Inject(method = "clearStatusEffects", at = @At("RETURN"))
    private void unbeta_putBack(CallbackInfoReturnable<Boolean> cir) {
        if (this.unbeta_keptZombification == null) return;
        LivingEntity self = (LivingEntity)(Object)this;
        self.addStatusEffect(this.unbeta_keptZombification);
        this.unbeta_keptZombification = null;
    }
}
