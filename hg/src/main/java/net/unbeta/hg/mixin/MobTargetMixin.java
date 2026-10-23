package net.unbeta.hg.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.unbeta.hg.Round;
import net.unbeta.hg.UnbetaHG;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mobs never go after players who are out of the round (they can't be hurt anyway). */
@Mixin(MobEntity.class)
public abstract class MobTargetMixin {

    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void unbetaHg_ignoreSpectres(LivingEntity target, CallbackInfo ci) {
        if (UnbetaHG.ACTIVE && target instanceof PlayerEntity p && Round.isOut(p)) ci.cancel();
    }
}
