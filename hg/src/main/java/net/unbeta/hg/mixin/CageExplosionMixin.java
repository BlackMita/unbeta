package net.unbeta.hg.mixin;

import net.minecraft.world.explosion.Explosion;
import net.unbeta.hg.Cage;
import net.unbeta.hg.UnbetaHG;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Explosions (creepers, Boom Spores, TNT) never break the spawn cage. */
@Mixin(Explosion.class)
public abstract class CageExplosionMixin {

    @Inject(method = "affectWorld", at = @At("HEAD"))
    private void unbetaHg_spareTheCage(boolean particles, CallbackInfo ci) {
        if (!UnbetaHG.ACTIVE) return;
        ((Explosion) (Object) this).getAffectedBlocks().removeIf(Cage::protects);
    }
}
