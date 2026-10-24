package net.unbeta.core.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.unbeta.core.gates.SpawnCatchAll;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Removed mobs are never added to a running world in the first place (cats, summons, eggs...). */
@Mixin(ServerWorld.class)
public abstract class GatedSpawnMixin {

    @Inject(method = "spawnEntity", at = @At("HEAD"), cancellable = true, require = 0)
    private void unbeta_refuseGated(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (SpawnCatchAll.gated(entity)) cir.setReturnValue(false);
    }
}
