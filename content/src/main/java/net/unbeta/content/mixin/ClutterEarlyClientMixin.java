package net.unbeta.content.mixin;

import net.minecraft.resource.ReloadableResourceManagerImpl;
import net.minecraft.resource.ResourceReload;
import net.unbeta.content.compat.ClutterEarly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** In your game, Clutter No More's shapes go in before its pictures and names are made (see ClutterEarly). */
@Mixin(ReloadableResourceManagerImpl.class)
public abstract class ClutterEarlyClientMixin {

    @Inject(method = "reload", at = @At("HEAD"))
    private void unbeta$clutterBeforeResources(CallbackInfoReturnable<ResourceReload> cir) {
        ClutterEarly.run("before the game's resources first loaded");
    }
}
