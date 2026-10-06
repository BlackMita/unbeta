package net.unbeta.content.mixin;

import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Direct slot access, for filling chests during world generation without triggering loot rolls. */
@Mixin(ChestBlockEntity.class)
public interface ChestInventoryAccessor {
    @Accessor("inventory")
    DefaultedList<ItemStack> unbeta_getInventory();
}
