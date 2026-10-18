package net.unbeta.content.mixin;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.unbeta.content.corruption.SpoiledMilk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** "Spoiled Milk Bucket", in a sickly green. */
@Mixin(Item.class)
public abstract class SpoiledMilkNameMixin {

    @Inject(method = "getName(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/text/Text;", at = @At("RETURN"), cancellable = true)
    private void unbeta_spoiledName(ItemStack stack, CallbackInfoReturnable<Text> cir) {
        if (SpoiledMilk.isSpoiled(stack)) {
            cir.setReturnValue(Text.literal("Spoiled ").append(cir.getReturnValue()).formatted(Formatting.DARK_GREEN));
        }
    }
}
