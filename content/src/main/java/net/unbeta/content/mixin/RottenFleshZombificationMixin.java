package net.unbeta.content.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.world.World;
import net.unbeta.content.corruption.Zombification;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Eating rotten flesh: whenever its Hunger roll hits, the eater is infected too. Hooked on
 * the exact call where vanilla applies a food's rolled effects, so infection follows the
 * roll itself rather than guessing afterwards. Gold armour does NOT protect - gold only
 * stops bites. Seared flesh is a different item and never infects.
 */
@Mixin(LivingEntity.class)
public abstract class RottenFleshZombificationMixin {

    /** The food being eaten, for the duration of applyFoodEffects. */
    @Unique private ItemStack unbeta_eating = ItemStack.EMPTY;

    @Inject(method = "applyFoodEffects", at = @At("HEAD"))
    private void unbeta_noteFood(ItemStack stack, World world, LivingEntity target, CallbackInfo ci) {
        this.unbeta_eating = stack;
    }

    @Inject(method = "applyFoodEffects", at = @At("TAIL"))
    private void unbeta_forgetFood(ItemStack stack, World world, LivingEntity target, CallbackInfo ci) {
        this.unbeta_eating = ItemStack.EMPTY;
    }

    @Redirect(method = "applyFoodEffects",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/entity/LivingEntity;addStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;)Z"))
    private boolean unbeta_fleshZombifies(LivingEntity target, StatusEffectInstance effect) {
        boolean applied = target.addStatusEffect(effect);
        if (!target.getWorld().isClient
                && target instanceof PlayerEntity
                && this.unbeta_eating.isOf(Items.ROTTEN_FLESH)
                && effect.getEffectType() == StatusEffects.HUNGER) {
            Zombification.inflict(target);
        }
        return applied;
    }
}
