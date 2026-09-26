package net.unbeta.content.mixin;

import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.mob.ZombieEntity;
import net.unbeta.content.corruption.ClosestPreyGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Zombies hunt the nearest prey - players or healthy livestock - instead of only players.
 * Vanilla's priority-2 target goal in initCustomGoals is the player-only hunt; it's
 * replaced by ClosestPreyGoal at the same priority. The revenge (1), iron golem (3) and
 * turtle (5) goals are untouched. Unmasons never reach this: they override initGoals.
 */
@Mixin(ZombieEntity.class)
public abstract class ZombieTargetMixin {

    @Inject(method = "initCustomGoals", at = @At("TAIL"))
    private void unbeta_huntNearestPrey(CallbackInfo ci) {
        ZombieEntity self = (ZombieEntity)(Object)this;
        self.targetSelector.getGoals().removeIf(
                g -> g.getPriority() == 2 && g.getGoal() instanceof ActiveTargetGoal);
        self.targetSelector.add(2, new ClosestPreyGoal(self));
    }
}
