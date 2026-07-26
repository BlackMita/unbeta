package net.unbeta.core.mixin;

import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.item.ItemStack;
import net.unbeta.core.api.UnbetaApi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Beta 1.8 animals cannot be bred. (They also no longer despawn, which is what makes
 * them capturable - that part is vanilla b1.8 behaviour we keep.)
 *
 * <p>Reporting "nothing is a breeding item" is the least invasive way to disable it:
 * no love mode, no hearts, no baby, and feeding simply does nothing.
 */
@Mixin(AnimalEntity.class)
public abstract class AnimalEntityMixin {

    @Inject(method = "isBreedingItem", at = @At("HEAD"), cancellable = true)
    private void unbeta$noBreeding(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!UnbetaApi.isReady()) return;
        if (UnbetaApi.rules().isSystemDisabled("breeding")) {
            cir.setReturnValue(false);
        }
    }
}
