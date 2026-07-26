package net.unbeta.core.mixin;

import net.minecraft.item.EnderEyeItem;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import net.minecraft.world.World;
import net.unbeta.core.gates.DimensionGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stops an Eye of Ender filling an End Portal Frame while the End is gated.
 *
 * <p>Belt and braces: the eye is already unobtainable (gated item, no recipe, no
 * creative entry, thrown entity discarded). This closes the last path - a /give in
 * a dev world, or an eye that survives in an old save.
 */
@Mixin(EnderEyeItem.class)
public abstract class EnderEyeItemMixin {

    @Inject(method = "useOnBlock", at = @At("HEAD"), cancellable = true)
    private void unbeta$blockFrameFill(ItemUsageContext context,
                                       CallbackInfoReturnable<ActionResult> cir) {
        if (DimensionGate.isGated(World.END)) {
            cir.setReturnValue(ActionResult.FAIL);
        }
    }
}
