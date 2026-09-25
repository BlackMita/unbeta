package net.unbeta.content.mixin;

import net.minecraft.entity.mob.MobEntity;
import net.unbeta.content.zombie.RisingMobAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Exposes MobEntity's protected initGoals() so a mob whose goals were cleared during a
 * rise can have them rebuilt on arrival.
 */
@Mixin(MobEntity.class)
public abstract class MobGoalReinitMixin implements RisingMobAccess.GoalReinit {

    @Shadow protected abstract void initGoals();

    @Override
    public void unbeta_reinitGoals() {
        this.initGoals();
    }
}
