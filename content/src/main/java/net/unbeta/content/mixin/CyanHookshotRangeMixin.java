package net.unbeta.content.mixin;

import net.minecraft.item.ItemStack;
import net.unbeta.content.hookshot.CyanHookshot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * The hookshot mod asks this helper for an item's upgrades whenever it fires (and for its
 * tooltip). A Cyan Hookshot always answers with Range among them: double reach, built in.
 */
@Pseudo
@Mixin(targets = "dev.cammiescorner.hookshot.util.UpgradesHelper", remap = false)
public abstract class CyanHookshotRangeMixin {

    @Inject(method = "getUpgrades", at = @At("RETURN"), cancellable = true, remap = false)
    private static void unbeta_cyanAlwaysRanged(ItemStack stack, CallbackInfoReturnable<List<Object>> cir) {
        if (!CyanHookshot.is(stack)) return;
        Object range = CyanHookshot.rangeUpgrade();
        List<Object> upgrades = cir.getReturnValue();
        if (range == null || upgrades.contains(range)) return;
        List<Object> withRange = new ArrayList<>(upgrades);
        withRange.add(range);
        cir.setReturnValue(withRange);
    }
}
