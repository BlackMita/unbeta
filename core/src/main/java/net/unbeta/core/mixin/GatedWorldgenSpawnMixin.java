package net.unbeta.core.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.world.ChunkRegion;
import net.unbeta.core.gates.SpawnCatchAll;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Removed mobs are never written into new chunks by world generation either: the villagers,
 * iron golems and cats a village structure would place are simply not made.
 */
@Mixin(ChunkRegion.class)
public abstract class GatedWorldgenSpawnMixin {

    @Inject(method = "spawnEntity", at = @At("HEAD"), cancellable = true, require = 0)
    private void unbeta_refuseGated(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (SpawnCatchAll.gated(entity)) cir.setReturnValue(false);
    }
}
