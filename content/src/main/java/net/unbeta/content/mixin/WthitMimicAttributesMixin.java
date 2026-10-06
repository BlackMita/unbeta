package net.unbeta.content.mixin;

import mcp.mobius.waila.api.IEntityAccessor;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.ITooltip;
import net.unbeta.content.compat.wthit.MimicWthitClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A chest has no health or armour: WTHIT's health/armour lines are skipped while a mimic is disguised. */
@Pseudo
@Mixin(targets = "mcp.mobius.waila.plugin.vanilla.provider.EntityAttributesProvider", remap = false)
public abstract class WthitMimicAttributesMixin {

    @Inject(method = "appendHead(Lmcp/mobius/waila/api/ITooltip;Lmcp/mobius/waila/api/IEntityAccessor;Lmcp/mobius/waila/api/IPluginConfig;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void unbeta_noHealthWhileDisguised(ITooltip tooltip, IEntityAccessor accessor, IPluginConfig config, CallbackInfo ci) {
        if (MimicWthitClient.disguised(accessor.getEntity())) ci.cancel();
    }

    @Inject(method = "appendBody(Lmcp/mobius/waila/api/ITooltip;Lmcp/mobius/waila/api/IEntityAccessor;Lmcp/mobius/waila/api/IPluginConfig;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void unbeta_noArmourWhileDisguised(ITooltip tooltip, IEntityAccessor accessor, IPluginConfig config, CallbackInfo ci) {
        if (MimicWthitClient.disguised(accessor.getEntity())) ci.cancel();
    }
}
