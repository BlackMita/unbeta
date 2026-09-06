package net.unbeta.content.mixin;

import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes gold tools behave as if enchanted with Silk Touch, invisibly.
 * Injects SILK_TOUCH into the NbtList returned by ItemStack.getEnchantments(),
 * which is what loot-table conditions actually read. No visual change.
 */
@Mixin(ItemStack.class)
public class GoldToolSilkTouchMixin {

    @Inject(method = "getEnchantments()Lnet/minecraft/nbt/NbtList;",
            at = @At("RETURN"), cancellable = true)
    private void unbeta_goldSilkTouch(CallbackInfoReturnable<NbtList> cir) {
        ItemStack self = (ItemStack)(Object)this;
        var item = self.getItem();
        boolean isGoldTool = item == Items.GOLDEN_PICKAXE
                || item == Items.GOLDEN_AXE
                || item == Items.GOLDEN_SHOVEL
                || item == Items.GOLDEN_HOE
                || item == Items.GOLDEN_SWORD;
        if (!isGoldTool) return;

        // Copy the list and inject silk touch entry
        NbtList original = cir.getReturnValue();
        NbtList copy = new NbtList();
        for (int i = 0; i < original.size(); i++) {
            copy.add(original.get(i).copy());
        }
        // Silk touch for all gold tools
        var silkId = Registries.ENCHANTMENT.getId(Enchantments.SILK_TOUCH);
        if (silkId != null) {
            NbtCompound silkNbt = new NbtCompound();
            silkNbt.putString("id", silkId.toString());
            silkNbt.putShort("lvl", (short)1);
            copy.add(silkNbt);
        }
        // Looting III for gold sword
        if (item == Items.GOLDEN_SWORD) {
            var lootId = Registries.ENCHANTMENT.getId(Enchantments.LOOTING);
            if (lootId != null) {
                NbtCompound lootNbt = new NbtCompound();
                lootNbt.putString("id", lootId.toString());
                lootNbt.putShort("lvl", (short)3);
                copy.add(lootNbt);
            }
        }
        cir.setReturnValue(copy);
    }
}
