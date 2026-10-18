package net.unbeta.content.mixin;

import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.MilkBucketItem;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.world.World;
import net.unbeta.content.bucket.MilkVariantItem;
import net.unbeta.content.corruption.SpoiledMilk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Spoiled milk (from a zombie cow) cures nothing: it poisons, starves, and may infect. */
@Mixin(MilkBucketItem.class)
public abstract class SpoiledMilkMixin {

    @Inject(method = "finishUsing", at = @At("HEAD"), cancellable = true)
    private void unbeta_spoiled(ItemStack stack, World world, LivingEntity user, CallbackInfoReturnable<ItemStack> cir) {
        if (!SpoiledMilk.isSpoiled(stack)) return;
        if (user instanceof ServerPlayerEntity sp) {
            Criteria.CONSUME_ITEM.trigger(sp, stack);
            sp.incrementStat(Stats.USED.getOrCreateStat(stack.getItem()));
        }
        if (!world.isClient) SpoiledMilk.sicken(user);
        ItemStack empty = new ItemStack(stack.getItem() instanceof MilkVariantItem mv ? mv.emptyBucket() : Items.BUCKET);
        if (user instanceof PlayerEntity p && !p.getAbilities().creativeMode) stack.decrement(1);
        cir.setReturnValue(stack.isEmpty() ? empty : stack);
    }
}
