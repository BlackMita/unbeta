package net.unbeta.core.mixin;

import net.minecraft.recipe.RecipeManager;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;
import net.unbeta.core.UnbetaCore;
import net.unbeta.core.gates.RecipeGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * Filters recipes at the tail of RecipeManager#apply, after vanilla has finished
 * parsing them from datapacks. The client receives the already-filtered set through
 * normal recipe sync, so no separate client-side gate is needed.
 */
@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {

    @Inject(
        method = "apply(Ljava/util/Map;Lnet/minecraft/resource/ResourceManager;Lnet/minecraft/util/profiler/Profiler;)V",
        at = @At("TAIL")
    )
    private void unbeta$filterAfterApply(Map<Identifier, ?> map,
                                         ResourceManager resourceManager,
                                         Profiler profiler,
                                         CallbackInfo ci) {
        try {
            RecipeGate.filter((RecipeManager) (Object) this);
        } catch (Throwable t) {
            UnbetaCore.LOG.error("Recipe filtering failed; recipes left untouched.", t);
        }
    }
}
