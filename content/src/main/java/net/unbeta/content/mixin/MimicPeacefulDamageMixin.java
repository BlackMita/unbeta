package net.unbeta.content.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.world.Difficulty;
import net.unbeta.content.mimic.MimicAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** In Peaceful a mimic's attacks land on nothing: no damage, no knockback, whoever it bites. */
@Mixin(LivingEntity.class)
public abstract class MimicPeacefulDamageMixin {

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void unbeta_toothlessInPeaceful(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (source.getAttacker() instanceof MimicAccess
                && self.getWorld().getDifficulty() == Difficulty.PEACEFUL) {
            cir.setReturnValue(false);
        }
    }
}
