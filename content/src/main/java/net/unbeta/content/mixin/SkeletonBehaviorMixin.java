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
 * - Remove AvoidSunlightGoal too. By day it switches navigation into avoid-sunlight
 *   mode, which truncates any path at the first sky-visible block whenever the
 *   skeleton is currently shaded - so a skeleton under a tree at noon could never
 *   plan a route out, whether wandering or chasing. Only affected bare-headed skeletons.
 * - Add SwimGoal. Vanilla skeletons have none, so in deep water they sink and walk the
 *   bottom, and can't path out of pools with walls higher than one block.
 * - Out of 7 skeletons: 4 spawn with a bow, 2 with a worn wooden sword, 1 empty-handed
 */
@Mixin(AbstractSkeletonEntity.class)
public class SkeletonBehaviorMixin {

    @Inject(method = "initGoals", at = @At("TAIL"))
    private void unbeta_removeSunlightGoal(CallbackInfo ci) {
        AbstractSkeletonEntity self = (AbstractSkeletonEntity)(Object)this;
        // Remove EscapeSunlightGoal by rebuilding without it
        self.goalSelector.getGoals().removeIf(
            goal -> goal.getGoal() instanceof net.minecraft.entity.ai.goal.EscapeSunlightGoal
                 || goal.getGoal() instanceof net.minecraft.entity.ai.goal.AvoidSunlightGoal
        );
        // Same swim goal creepers and spiders use: keeps them at the surface.
        self.goalSelector.add(1, new net.minecraft.entity.ai.goal.SwimGoal(self));
    }

    @Inject(method = "initEquipment", at = @At("TAIL"))
    private void unbeta_maybeSwordInstead(Random random, LocalDifficulty difficulty,
                                           CallbackInfo ci) {
        AbstractSkeletonEntity self = (AbstractSkeletonEntity)(Object)this;
        // Out of 7: 4 keep their bow, 2 get a worn wooden sword, 1 comes empty-handed
        // (and grabs - see SkeletonGrip).
        int roll = random.nextInt(7);
        if (roll == 6) {
            self.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        } else if (roll >= 4) {
            ItemStack sword = new ItemStack(Items.WOODEN_SWORD);
            // Damage it randomly between 25% and 90% worn
            int maxDamage = sword.getMaxDamage();
            int damage = maxDamage / 4 + random.nextInt(maxDamage * 3 / 4);
            sword.setDamage(damage);
            self.equipStack(EquipmentSlot.MAINHAND, sword);
        }
    }
}
