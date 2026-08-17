package net.unbeta.content.mixin;

import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.item.ItemStack;
import net.unbeta.content.furnace.FurnaceIgnitionTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractFurnaceBlockEntity.class)
public class FurnaceIgnitionMixin {
    @Inject(method = "getFuelTime", at = @At("HEAD"), cancellable = true)
    private void unbeta_requireIgnition(ItemStack fuel, CallbackInfoReturnable<Integer> cir) {
        AbstractFurnaceBlockEntity self = (AbstractFurnaceBlockEntity)(Object)this;
        if (self.getPos() != null && !FurnaceIgnitionTracker.isIgnited(self.getPos())) {
            cir.setReturnValue(0);
        }
    }
}
