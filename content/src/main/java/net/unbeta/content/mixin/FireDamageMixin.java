package net.unbeta.content.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.registry.tag.DamageTypeTags;
import net.unbeta.content.fire.FireDamage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fire damage of every kind (vanilla's IS_FIRE tag: burning, standing in fire, lava, magma
 * blocks, fireballs) is doubled, for every living thing. The hit is re-dealt at double
 * strength through the entity's own damage(), so armour, invulnerability and every other
 * rule still apply. Endermen are unaffected: they can't be damaged at all.
 */
@Mixin(LivingEntity.class)
public abstract class FireDamageMixin {

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void unbeta_doubleFire(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (FireDamage.DOUBLING.get() || !source.isIn(DamageTypeTags.IS_FIRE)) return;
        FireDamage.DOUBLING.set(Boolean.TRUE);
        try {
            cir.setReturnValue(((LivingEntity)(Object)this).damage(source, amount * FireDamage.MULTIPLIER));
        } finally {
            FireDamage.DOUBLING.set(Boolean.FALSE);
        }
    }
}
