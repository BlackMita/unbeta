package net.unbeta.content.mixin;

import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameterSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No emeralds in Unbeta: any a chest's loot would hold come out as gold nuggets, one for one. */
@Mixin(LootTable.class)
public abstract class ChestEmeraldMixin {

    @Inject(method = "supplyInventory", at = @At("TAIL"))
    private void unbeta_emeraldsToNuggets(Inventory inventory, LootContextParameterSet parameters, long seed, CallbackInfo ci) {
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack s = inventory.getStack(i);
            if (s.isOf(Items.EMERALD)) inventory.setStack(i, new ItemStack(Items.GOLD_NUGGET, s.getCount()));
        }
    }
}
