package net.unbeta.content.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Gliding over ice makes no footsteps (see IceSlideMixin). */
@Mixin(Entity.class)
public abstract class IceStepMixin {

    @Inject(method = "playStepSounds", at = @At("HEAD"), cancellable = true)
    private void unbeta_silentOnIce(BlockPos pos, BlockState state, CallbackInfo ci) {
        if ((Object) this instanceof LivingEntity && state.getBlock().getSlipperiness() >= 0.97F) ci.cancel();
    }
}
