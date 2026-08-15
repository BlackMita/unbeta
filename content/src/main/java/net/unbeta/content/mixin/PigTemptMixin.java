package net.unbeta.content.mixin;

import net.minecraft.entity.ai.goal.TemptGoal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.Items;
import net.minecraft.recipe.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PigEntity.class)
public abstract class PigTemptMixin extends MobEntity {
    protected PigTemptMixin() { super(null, null); }

    @Inject(method = "initGoals", at = @At("TAIL"))
    private void unbeta_pigFollowApple(CallbackInfo ci) {
        this.goalSelector.add(4, new TemptGoal(
                (PigEntity)(Object)this, 1.2,
                Ingredient.ofItems(Items.APPLE), false));
    }
}
