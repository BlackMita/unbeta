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
        // initGoals() fills BOTH selectors. RisingMob only cleared goalSelector, so
        // re-running it used to stack a second copy of every target goal.
        ((MobEntity)(Object)this).goalSelector.clear(g -> true);
        ((MobEntity)(Object)this).targetSelector.clear(g -> true);
        this.initGoals();
    }
}
