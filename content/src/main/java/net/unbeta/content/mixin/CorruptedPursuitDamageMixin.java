package net.unbeta.content.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.unbeta.content.corruption.CorruptedAnimal;
import net.unbeta.content.corruption.PursuitSpeed;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A corrupted animal that takes a hit loses its built-up speed, like zombies do. */
@Mixin(LivingEntity.class)
public abstract class CorruptedPursuitDamageMixin {

    @Inject(method = "damage", at = @At("HEAD"))
    private void unbeta_resetOnDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if ((Object)this instanceof CorruptedAnimal && (Object)this instanceof MobEntity mob) {
            PursuitSpeed.reset(mob);
        }
    }
}
