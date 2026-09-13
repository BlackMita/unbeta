package net.unbeta.content.mixin;

import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Locked chests refuse automation.
 *
 * <p>Hooked at canInsert/canExtract rather than the insert/extract call sites: every
 * transfer path has to clear one of these two, so patching here covers paths we
 * haven't enumerated instead of only the ones we happened to find.
 */
@Mixin(HopperBlockEntity.class)
public class LockedChestHopperMixin {

    @Inject(method = "canInsert", at = @At("HEAD"), cancellable = true)
    private static void unbeta_denyInsert(Inventory inventory, ItemStack stack, int slot,
                                          Direction side, CallbackInfoReturnable<Boolean> cir) {
        if (net.unbeta.content.lockey.LockeyInventories.isLocked(inventory)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "canExtract", at = @At("HEAD"), cancellable = true)
    private static void unbeta_denyExtract(Inventory hopperInventory, Inventory fromInventory,
                                           ItemStack stack, int slot, Direction facing,
                                           CallbackInfoReturnable<Boolean> cir) {
        if (net.unbeta.content.lockey.LockeyInventories.isLocked(fromInventory)) {
            cir.setReturnValue(false);
        }
    }
}
