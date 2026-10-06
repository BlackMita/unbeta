package net.unbeta.content.mixin;

import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsage;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.unbeta.content.bucket.BucketItems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Cows can be milked into wood and copper buckets (vanilla only accepts the iron one). Never zombie cows. */
@Mixin(CowEntity.class)
public abstract class CowMilkingMixin {

    @Inject(method = "interactMob", at = @At("HEAD"), cancellable = true)
    private void unbeta_milkOtherBuckets(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        CowEntity self = (CowEntity)(Object)this;
        if (self instanceof net.unbeta.content.corruption.CorruptedAnimal || self.isBaby()) return;
        ItemStack held = player.getStackInHand(hand);
        Item milk = held.isOf(BucketItems.WOOD_BUCKET) ? BucketItems.WOOD_MILK_BUCKET
                : held.isOf(BucketItems.COPPER_BUCKET) ? BucketItems.COPPER_MILK_BUCKET : null;
        if (milk == null) return;
        player.playSound(SoundEvents.ENTITY_COW_MILK, 1.0f, 1.0f);
        player.setStackInHand(hand, ItemUsage.exchangeStack(held, player, new ItemStack(milk)));
        cir.setReturnValue(ActionResult.success(self.getWorld().isClient));
    }
}
