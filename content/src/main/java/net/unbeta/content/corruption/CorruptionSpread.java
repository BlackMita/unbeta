package net.unbeta.content.corruption;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

/**
 * Any damage from a carrier (zombie or corrupted animal) to healthy livestock marks the
 * animal as bitten - hidden, and saved with the entity. When it later dies, by any cause
 * other than a clean death, its chunk remembers its corrupted form.
 */
public final class CorruptionSpread {

    private CorruptionSpread() {}

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!entity.getWorld().isClient
                    && CorruptionMemory.isHealthyLivestock(entity)
                    && CorruptionMemory.isCarrier(source.getAttacker())) {
                entity.addCommandTag(CorruptionMemory.BITTEN_TAG);
            }
            return true; // never blocks the damage itself
        });
    }
}
