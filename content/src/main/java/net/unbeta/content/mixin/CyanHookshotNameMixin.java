package net.unbeta.content.mixin;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.unbeta.content.hookshot.CyanHookshot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The Cyan Hookshot is just "Hookshot" (a renamed one keeps its custom name, as usual). */
@Mixin(Item.class)
public abstract class CyanHookshotNameMixin {

    @Inject(method = "getName(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/text/Text;", at = @At("HEAD"), cancellable = true)
    private void unbeta_plainHookshot(ItemStack stack, CallbackInfoReturnable<Text> cir) {
        if (CyanHookshot.is(stack)) cir.setReturnValue(Text.translatable("item.unbeta-content.hookshot"));
    }
}
