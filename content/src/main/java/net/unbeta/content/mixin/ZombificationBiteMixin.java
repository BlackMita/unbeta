package net.unbeta.content.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.unbeta.content.corruption.CorruptionMemory;
import net.unbeta.content.corruption.Zombification;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A hit from a carrier (zombie or corrupted animal) has a 1 in 4 chance to inflict
 * Inevitable Zombification - but only a hit that actually landed. damage() returns false
 * for a shield block or a hit during the invulnerability flash, so those never infect.
 * Any worn gold armour blocks bite infection. Reinfection resets the full 10 minutes.
 */
@Mixin(LivingEntity.class)
public abstract class ZombificationBiteMixin {

    private static final int BITE_ODDS = 4;

    @Inject(method = "damage", at = @At("RETURN"))
    private void unbeta_zombifyingBite(DamageSource source, float amount,
                                       CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;               // blocked, or invulnerable
        LivingEntity self = (LivingEntity)(Object)this;
        if (self.getWorld().isClient) return;
        if (!(self instanceof PlayerEntity player) || !player.isAlive()) return;
        if (!CorruptionMemory.isCarrier(source.getAttacker())) return;
        if (Zombification.isWearingGold(player)) return;
        if (player.getRandom().nextInt(BITE_ODDS) != 0) return;
        Zombification.inflict(player);
    }
}
