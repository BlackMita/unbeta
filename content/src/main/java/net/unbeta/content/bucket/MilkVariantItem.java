package net.unbeta.content.bucket;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.MilkBucketItem;
import net.minecraft.world.World;

import java.util.function.Supplier;

/**
 * Milk in a wood or copper bucket. Drinks exactly like vanilla milk (clearing effects, with
 * Inevitable Zombification kept by ZombificationMilkMixin), but hands back the right empty
 * bucket. Copper milk leaves you with Poison I for 5 seconds.
 */
public class MilkVariantItem extends MilkBucketItem {

    private final Supplier<Item> empty;
    private final boolean poisons;

    public MilkVariantItem(Settings settings, Supplier<Item> empty, boolean poisons) {
        super(settings);
        this.empty = empty;
        this.poisons = poisons;
    }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        ItemStack result = super.finishUsing(stack, world, user);
        if (poisons && !world.isClient) {
            user.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 20 * 5, 0));
        }
        return result.isOf(Items.BUCKET) ? new ItemStack(empty.get()) : result;
    }
}
