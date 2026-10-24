package net.unbeta.content.mixin;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.FoodComponent;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Cloud Boots' golden feather can be eaten: no hunger back, but 10 seconds of Levitation IV
 * (the Goldreath bullet's lift). Edible even when full.
 */
@Mixin(Item.class)
public abstract class GoldenFeatherFoodMixin {

    @Unique private static final Identifier UNBETA_FEATHER = new Identifier("cloudboots", "golden_feather");
    @Unique private static FoodComponent unbeta_food;

    @Unique
    private boolean unbeta_isFeather() {
        return Registries.ITEM.getId((Item) (Object) this).equals(UNBETA_FEATHER);
    }

    @Unique
    private static FoodComponent unbeta_food() {
        if (unbeta_food == null) {
            unbeta_food = new FoodComponent.Builder().hunger(0).saturationModifier(0F).alwaysEdible()
                    .statusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 200, 3), 1.0F).build();
        }
        return unbeta_food;
    }

    @Inject(method = "isFood", at = @At("HEAD"), cancellable = true)
    private void unbeta_featherIsFood(CallbackInfoReturnable<Boolean> cir) {
        if (unbeta_isFeather()) cir.setReturnValue(true);
    }

    @Inject(method = "getFoodComponent", at = @At("HEAD"), cancellable = true)
    private void unbeta_featherFood(CallbackInfoReturnable<FoodComponent> cir) {
        if (unbeta_isFeather()) cir.setReturnValue(unbeta_food());
    }
}
