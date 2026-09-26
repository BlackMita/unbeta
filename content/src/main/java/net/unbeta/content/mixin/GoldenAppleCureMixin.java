package net.unbeta.content.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.world.World;
import net.unbeta.content.corruption.Zombification;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Eating a golden apple (or an enchanted one) cures Inevitable Zombification. */
@Mixin(LivingEntity.class)
public abstract class GoldenAppleCureMixin {

    @Inject(method = "eatFood", at = @At("HEAD"))
    private void unbeta_cureZombification(World world, ItemStack stack,
                                          CallbackInfoReturnable<ItemStack> cir) {
        if (world.isClient) return;
        if (stack.isOf(Items.GOLDEN_APPLE) || stack.isOf(Items.ENCHANTED_GOLDEN_APPLE)) {
            ((LivingEntity)(Object)this).removeStatusEffect(Zombification.EFFECT);
        }
    }
}
