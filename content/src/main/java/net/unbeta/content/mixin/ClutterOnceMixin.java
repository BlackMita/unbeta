package net.unbeta.content.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Clutter No More adds its vertical slabs and steps while the game builds its block list. That
 * normally happens once, at start-up; but newer Fabric API builds the list a second time when a
 * server starts (a dedicated server, or singleplayer's own), and by then no blocks may be added:
 * Clutter's second go crashes the server. Its blocks are all in place after the first go, so
 * every later one is skipped.
 */
@Pseudo
@Mixin(targets = "dev.tazer.clutternomore.registry.ShapeBlocks", remap = false)
public abstract class ClutterOnceMixin {

    @Unique private static boolean unbeta$registered;

    @Inject(method = "registerVariants", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void unbeta$onlyOnce(CallbackInfo ci) {
        if (unbeta$registered) ci.cancel();
        else unbeta$registered = true;
    }
}
