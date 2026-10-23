package net.unbeta.hg.mixin.client;

import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.util.Formatting;
import net.unbeta.hg.client.KeepsClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** The item name that pops up over the hotbar when you switch items is green for a kept item. */
@Mixin(InGameHud.class)
public abstract class KeptHeldNameMixin {

    @Shadow
    private ItemStack currentStack;

    @ModifyVariable(method = "renderHeldItemTooltip", at = @At("STORE"), ordinal = 0)
    private MutableText unbetaHg_greenIfKept(MutableText name) {
        if (KeepsClient.onHgServer && !this.currentStack.isEmpty() && KeepsClient.isKept(this.currentStack.getItem())) {
            return name.formatted(Formatting.GREEN);
        }
        return name;
    }
}
