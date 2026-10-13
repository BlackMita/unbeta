package net.unbeta.content.mixin;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.unbeta.content.lockey.LockeyDoors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A damaged door shows how much of it is left as a durability bar (6 pieces = full). */
@Mixin(Item.class)
public abstract class DamagedDoorBarMixin {

    private static float unbeta_left(ItemStack stack) {
        return (LockeyDoors.RELEASES - LockeyDoors.itemDamage(stack)) / (float) LockeyDoors.RELEASES;
    }

    @Inject(method = "isItemBarVisible", at = @At("HEAD"), cancellable = true)
    private void unbeta_doorBarVisible(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (LockeyDoors.itemDamage(stack) > 0) cir.setReturnValue(true);
    }

    @Inject(method = "getItemBarStep", at = @At("HEAD"), cancellable = true)
    private void unbeta_doorBarStep(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (LockeyDoors.itemDamage(stack) > 0) cir.setReturnValue(Math.round(13.0F * unbeta_left(stack)));
    }

    @Inject(method = "getItemBarColor", at = @At("HEAD"), cancellable = true)
    private void unbeta_doorBarColor(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (LockeyDoors.itemDamage(stack) > 0) cir.setReturnValue(MathHelper.hsvToRgb(unbeta_left(stack) / 3.0F, 1.0F, 1.0F));
    }
}
