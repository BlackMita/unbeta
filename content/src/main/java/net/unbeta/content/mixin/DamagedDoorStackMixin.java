package net.unbeta.content.mixin;

import net.minecraft.item.ItemStack;
import net.unbeta.content.lockey.LockeyDoors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Damaged doors don't stack. */
@Mixin(ItemStack.class)
public abstract class DamagedDoorStackMixin {

    @Inject(method = "getMaxCount", at = @At("RETURN"), cancellable = true)
    private void unbeta_damagedDoorAlone(CallbackInfoReturnable<Integer> cir) {
        if (LockeyDoors.itemDamage((ItemStack) (Object) this) > 0) cir.setReturnValue(1);
    }
}
