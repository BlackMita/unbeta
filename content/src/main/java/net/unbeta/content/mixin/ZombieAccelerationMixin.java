package net.unbeta.content.mixin;

import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.ZombieEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * Zombies slowly accelerate while pursuing a target. Speed resets when:
 * - They deal damage (tryAttack succeeds)
 * - They take damage
 * - They lose their target
 *
 * Acceleration accumulates in a per-instance field. Max bonus caps at 100%
 * of base speed (so a zombie maxes out at 2x normal walking speed).
 */
@Mixin(ZombieEntity.class)
public abstract class ZombieAccelerationMixin {

    private static final UUID PURSUIT_SPEED_UUID =
            UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final String MODIFIER_NAME = "unbeta:zombie_pursuit";

    /** Accumulated speed bonus, 0.0 to MAX_BONUS. */
    private float unbeta_pursuitBonus = 0.0f;

    /** Per-tick acceleration added while pursuing. Tune this value. */
    private static final float ACCEL_PER_TICK = 0.0003f;

    /** Maximum bonus as a fraction of base speed (1.0 = 100% bonus = 2x speed). */
    private static final float MAX_BONUS = 1.0f;

    @Inject(method = "tickMovement", at = @At("HEAD"))
    private void unbeta_pursuitAccelerate(CallbackInfo ci) {
        ZombieEntity self = (ZombieEntity)(Object)this;
        if (self.getWorld().isClient) return;

        if (self.getTarget() != null) {
            // Accumulate speed bonus
            unbeta_pursuitBonus = Math.min(unbeta_pursuitBonus + ACCEL_PER_TICK, MAX_BONUS);
            applySpeedBonus(self);
        } else {
            // Lost target — reset
            resetSpeed(self);
        }
    }

    /** Reset speed on damage taken. */
    @Inject(method = "damage", at = @At("HEAD"))
    private void unbeta_resetOnDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        ZombieEntity self = (ZombieEntity)(Object)this;
        resetSpeed(self);
    }

    /** Reset speed when zombie successfully hits target. */
    @Inject(method = "tryAttack", at = @At("RETURN"))
    private void unbeta_resetOnAttack(net.minecraft.entity.Entity target,
                                       CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            resetSpeed((ZombieEntity)(Object)this);
        }
    }

    private void applySpeedBonus(ZombieEntity self) {
        var attr = self.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (attr == null) return;
        // Remove old modifier first
        attr.removeModifier(PURSUIT_SPEED_UUID);
        if (unbeta_pursuitBonus > 0) {
            attr.addTemporaryModifier(new EntityAttributeModifier(
                    PURSUIT_SPEED_UUID, MODIFIER_NAME,
                    unbeta_pursuitBonus,
                    EntityAttributeModifier.Operation.MULTIPLY_BASE));
        }
    }

    private void resetSpeed(ZombieEntity self) {
        unbeta_pursuitBonus = 0.0f;
        var attr = self.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (attr != null) attr.removeModifier(PURSUIT_SPEED_UUID);
    }
}
