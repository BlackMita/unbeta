package net.unbeta.content.mixin;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnchantmentHelper.class)
public class GoldSwordLootingMixin {

    @Inject(method = "getEquipmentLevel(Lnet/minecraft/enchantment/Enchantment;Lnet/minecraft/entity/LivingEntity;)I",
            at = @At("HEAD"), cancellable = true)
    private static void unbeta_goldSwordLooting(Enchantment enchantment, LivingEntity entity,
                                                 CallbackInfoReturnable<Integer> cir) {
        if (enchantment != Enchantments.LOOTING) return;
        LivingEntity attacker = entity.getAttacker();
        if (attacker == null) return;
        if (attacker.getMainHandStack().isOf(Items.GOLDEN_SWORD)) {
            cir.setReturnValue(3);
        }
    }
}
