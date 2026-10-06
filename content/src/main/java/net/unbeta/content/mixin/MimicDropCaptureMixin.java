package net.unbeta.content.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.unbeta.content.mimic.MimicAccess;
import net.unbeta.content.mimic.MimicLocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** While a mimic's loot is being rolled into it, its "drops" go inside instead of onto the floor. */
@Mixin(Entity.class)
public abstract class MimicDropCaptureMixin {

    @Inject(method = "dropStack(Lnet/minecraft/item/ItemStack;F)Lnet/minecraft/entity/ItemEntity;",
            at = @At("HEAD"), cancellable = true)
    private void unbeta_captureMimicLoot(ItemStack stack, float yOffset, CallbackInfoReturnable<ItemEntity> cir) {
        if ((Object) this instanceof MimicAccess && MimicLocks.capture(stack)) cir.setReturnValue(null);
    }
}
