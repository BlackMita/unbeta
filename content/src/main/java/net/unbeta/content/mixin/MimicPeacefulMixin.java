package net.unbeta.content.mixin;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.world.Difficulty;
import net.unbeta.content.mimic.MimicAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Peaceful doesn't delete mimics - they carry on exactly as before (their bite just does nothing). */
@Mixin(MobEntity.class)
public abstract class MimicPeacefulMixin {

    @Inject(method = "checkDespawn", at = @At("HEAD"), cancellable = true)
    private void unbeta_mimicsStayInPeaceful(CallbackInfo ci) {
        MobEntity self = (MobEntity) (Object) this;
        if (self instanceof MimicAccess && self.getWorld().getDifficulty() == Difficulty.PEACEFUL) ci.cancel();
    }
}
