package net.unbeta.content.mixin;

import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.item.ItemStack;
import net.unbeta.content.furnace.FurnaceIgnitionTracker;
import net.unbeta.content.furnace.LavaFuel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Unbeta furnaces must be lit before fuel burns: an unlit furnace reports every fuel as
 * worth 0. Lava buckets are the exception - lava IS the flame - so any lava bucket lights
 * the furnace and burns for its full time. It's also what makes the fuel slot accept wood
 * and copper lava buckets at all.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public class FurnaceIgnitionMixin {

    @Inject(method = "getFuelTime", at = @At("HEAD"), cancellable = true)
    private void unbeta_requireIgnition(ItemStack fuel, CallbackInfoReturnable<Integer> cir) {
        AbstractFurnaceBlockEntity self = (AbstractFurnaceBlockEntity)(Object)this;
        int lava = LavaFuel.burnTime(fuel);
        if (lava > 0) {
            if (self.getPos() != null) FurnaceIgnitionTracker.ignite(self.getPos());
            cir.setReturnValue(lava);
            return;
        }
        if (self.getPos() != null && !FurnaceIgnitionTracker.isIgnited(self.getPos())) {
            cir.setReturnValue(0);
        }
    }

    @Inject(method = "canUseAsFuel", at = @At("HEAD"), cancellable = true)
    private static void unbeta_lavaIsFuel(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (LavaFuel.burnTime(stack) > 0) cir.setReturnValue(true);
    }
}
