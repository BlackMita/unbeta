package net.unbeta.content.mixin;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.SimpleRegistry;
import net.unbeta.content.compat.ClutterEarly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Clutter No More's shapes go in just before the block list closes, if not in already (see ClutterEarly). */
@Mixin(SimpleRegistry.class)
public abstract class ClutterEarlyMixin {

    @Inject(method = "freeze", at = @At("HEAD"))
    private void unbeta$clutterBeforeBlocksClose(CallbackInfoReturnable<Registry<?>> cir) {
        if ((Object) this == Registries.BLOCK) ClutterEarly.run("before the block list closed");
    }
}
