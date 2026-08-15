package net.unbeta.content.mixin;

import net.minecraft.entity.ai.goal.TemptGoal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.item.Item;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChickenEntity.class)
public abstract class ChickenTemptMixin extends MobEntity {
    protected ChickenTemptMixin() { super(null, null); }

    @Inject(method = "initGoals", at = @At("TAIL"))
    private void unbeta_chickenFollowSeeds(CallbackInfo ci) {
        Item[] seeds = Registries.ITEM.stream()
                .filter(i -> Registries.ITEM.getId(i).getPath().contains("seed"))
                .toArray(Item[]::new);
        if (seeds.length > 0) {
            this.goalSelector.add(3, new TemptGoal(
                    (ChickenEntity)(Object)this, 1.0,
                    Ingredient.ofItems(seeds), false));
        }
    }
}
