package net.unbeta.content.zombie;

import net.minecraft.entity.mob.MobEntity;

/**
 * initGoals() is protected on MobEntity, so it can't be called from here directly.
 * An interface implemented onto MobEntity by mixin exposes it.
 */
public final class RisingMobAccess {
    private RisingMobAccess() {}

    public interface GoalReinit {
        void unbeta_reinitGoals();
    }

    public static void reinitGoals(MobEntity mob) {
        ((GoalReinit) mob).unbeta_reinitGoals();
    }
}
