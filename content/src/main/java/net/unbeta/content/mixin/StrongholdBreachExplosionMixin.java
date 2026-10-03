package net.unbeta.content.mixin;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import net.unbeta.content.stronghold.Breach;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * An explosion inside the stronghold is ONE breach, however many blocks it takes out.
 *
 * <p>Hooked at the end of collectBlocksAndDamageEntities: by then the explosion knows every
 * block it will destroy (getAffectedBlocks), but hasn't removed any of them yet, so the
 * original blocks are still there to be read and remembered.
 */
@Mixin(Explosion.class)
public abstract class StrongholdBreachExplosionMixin {

    @Shadow @Final private World world;

    @Inject(method = "collectBlocksAndDamageEntities", at = @At("TAIL"))
    private void unbeta_recordBreach(CallbackInfo ci) {
        Explosion self = (Explosion)(Object)this;
        if (this.world instanceof ServerWorld sw) {
            Breach.record(sw, self.getAffectedBlocks());
        }
    }
}
