package net.unbeta.content.corruption;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Does nothing while it counts down; on its final tick it kills. The damage type
 * (data/unbeta-content/damage_type/zombification.json) is tagged to bypass armour,
 * enchantments, Resistance, shields, invulnerability and totems. Creative and spectator
 * players are spared.
 */
public class InevitableZombificationEffect extends StatusEffect {

    public InevitableZombificationEffect() {
        super(StatusEffectCategory.HARMFUL, 0x4E7A2E);
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return duration == 1; // the final tick of the countdown
    }

    @Override
    public void applyUpdateEffect(LivingEntity entity, int amplifier) {
        if (entity.getWorld().isClient) return;
        if (entity instanceof PlayerEntity p && (p.isCreative() || p.isSpectator())) return;
        entity.damage(Zombification.damageSource(entity.getWorld()), Float.MAX_VALUE);
    }
}
