package net.unbeta.content.corruption;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsage;
import net.minecraft.item.Items;
import net.minecraft.item.MilkBucketItem;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.unbeta.content.bucket.BucketItems;

/**
 * Milking a zombie cow (dangerous in itself) gives Spoiled Milk: drinking it clears nothing,
 * and gives Poison (10 s) and Hunger (30 s), with a 1 in 4 chance of Inevitable Zombification.
 * Spoiled Ice Milk freezes into Ice Scream instead of Ice Cream.
 */
public final class SpoiledMilk {

    public static final String NBT = "UnbetaSpoiled";

    private SpoiledMilk() {}

    public static boolean isSpoiled(ItemStack stack) {
        return stack.getItem() instanceof MilkBucketItem && stack.hasNbt() && stack.getNbt().getBoolean(NBT);
    }

    /** Right-clicking a zombie cow with an empty bucket. PASS if it isn't one. */
    public static ActionResult tryMilk(ZombieCowEntity cow, PlayerEntity player, Hand hand) {
        if (cow.isBaby()) return ActionResult.PASS;
        ItemStack held = player.getStackInHand(hand);
        Item milk = held.isOf(Items.BUCKET) ? Items.MILK_BUCKET
                : held.isOf(BucketItems.WOOD_BUCKET) ? BucketItems.WOOD_MILK_BUCKET
                : held.isOf(BucketItems.COPPER_BUCKET) ? BucketItems.COPPER_MILK_BUCKET
                : held.isOf(net.unbeta.content.bucket.IceBuckets.ICE_BUCKET) ? net.unbeta.content.bucket.IceBuckets.ICE_MILK_BUCKET : null;
        if (milk == null) return ActionResult.PASS;
        player.playSound(SoundEvents.ENTITY_COW_MILK, 1.0F, 0.7F);
        ItemStack filled = new ItemStack(milk);
        filled.getOrCreateNbt().putBoolean(NBT, true);
        if (milk == net.unbeta.content.bucket.IceBuckets.ICE_MILK_BUCKET) {
            net.unbeta.content.bucket.IceFreeze.start(filled, cow.getWorld());   // freezes into Ice Scream
        }
        player.setStackInHand(hand, ItemUsage.exchangeStack(held, player, filled));
        return ActionResult.success(cow.getWorld().isClient);
    }

    public static void sicken(LivingEntity user) {
        user.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 20 * 10, 0));
        user.addStatusEffect(new StatusEffectInstance(StatusEffects.HUNGER, 20 * 30, 1));
        if (user.getRandom().nextInt(4) == 0) Zombification.inflict(user);
    }
}
