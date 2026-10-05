package net.unbeta.content.corruption;

import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;

import java.util.UUID;

/**
 * The "gets faster the longer it chases you" ramp, for corrupted animals (zombies have their
 * own copy in ZombieAccelerationMixin). The current bonus is kept on the speed modifier
 * itself, so there's no per-entity counter to save or lose. Capped at double speed; reset
 * whenever the animal lands a hit, takes a hit, or loses its target.
 */
public final class PursuitSpeed {

    private static final UUID ID = UUID.fromString("5f0c2b1e-7d3a-4c8e-9b6f-2a1d4e7c9b30");
    private static final double MAX_BONUS = 1.0; // +100% = double speed

    private PursuitSpeed() {}

    public static void tick(MobEntity mob, float accelPerTick) {
        EntityAttributeInstance attr = mob.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (attr == null) return;
        if (mob.getTarget() == null) {
            attr.removeModifier(ID);
            return;
        }
        EntityAttributeModifier current = attr.getModifier(ID);
        double bonus = Math.min((current == null ? 0.0 : current.getValue()) + accelPerTick, MAX_BONUS);
        attr.removeModifier(ID);
        attr.addTemporaryModifier(new EntityAttributeModifier(ID, "unbeta:corrupted_pursuit",
                bonus, EntityAttributeModifier.Operation.MULTIPLY_BASE));
    }

    public static void reset(MobEntity mob) {
        EntityAttributeInstance attr = mob.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (attr != null) attr.removeModifier(ID);
    }
}
