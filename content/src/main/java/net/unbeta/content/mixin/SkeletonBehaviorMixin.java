package net.unbeta.content.mixin;

import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LocalDifficulty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Unbeta skeleton tweaks:
 * - Remove EscapeSunlightGoal (skeletons no longer burn so seeking shade is vestigial)
 * - 1/6 chance to spawn with a damaged wooden sword instead of a bow
 */
@Mixin(AbstractSkeletonEntity.class)
public class SkeletonBehaviorMixin {

    @Inject(method = "initGoals", at = @At("TAIL"))
    private void unbeta_removeSunlightGoal(CallbackInfo ci) {
        AbstractSkeletonEntity self = (AbstractSkeletonEntity)(Object)this;
        // Remove EscapeSunlightGoal by rebuilding without it
        self.goalSelector.getGoals().removeIf(
            goal -> goal.getGoal() instanceof net.minecraft.entity.ai.goal.EscapeSunlightGoal
        );
    }

    @Inject(method = "initEquipment", at = @At("TAIL"))
    private void unbeta_maybeSwordInstead(Random random, LocalDifficulty difficulty,
                                           CallbackInfo ci) {
        AbstractSkeletonEntity self = (AbstractSkeletonEntity)(Object)this;
        // 1/6 chance: replace bow with a damaged wooden sword
        if (random.nextInt(6) == 0) {
            ItemStack sword = new ItemStack(Items.WOODEN_SWORD);
            // Damage it randomly between 25% and 90% worn
            int maxDamage = sword.getMaxDamage();
            int damage = maxDamage / 4 + random.nextInt(maxDamage * 3 / 4);
            sword.setDamage(damage);
            self.equipStack(EquipmentSlot.MAINHAND, sword);
        }
    }
}
