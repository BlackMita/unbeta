package net.unbeta.content.mixin;

import net.minecraft.block.Blocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.unbeta.content.jackolantern.JackOLanternRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes the pumpkin blur overlay appear when wearing an Unbeta unlit JoL.
 * We redirect the isOf(CARVED_PUMPKIN) check — if player is wearing our JoL,
 * we return true so vanilla's own renderOverlay call fires at the correct point
 * in the render pipeline with correct blend state.
 */
@Mixin(ItemStack.class)
public class JolOverlayMixin {

    @Inject(method = "isOf", at = @At("RETURN"), cancellable = true)
    private void unbeta_jolActsAsCarvedPumpkin(net.minecraft.item.Item item,
                                               CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() && item == Blocks.CARVED_PUMPKIN.asItem()) {
            ItemStack self = (ItemStack)(Object)this;
            if (self.isOf(JackOLanternRegistry.UNLIT_ITEM)) {
                cir.setReturnValue(true);
            }
        }
    }
}
