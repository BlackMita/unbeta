package net.unbeta.content.bucket;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/** Ice Cream: eaten, bucket and all. A minute of Fire Resistance, and any fire on you goes out. */
public class IceCreamItem extends Item {

    public IceCreamItem(Settings settings) {
        super(settings);
    }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        ItemStack result = super.finishUsing(stack, world, user);
        if (!world.isClient) {
            user.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 20 * 60, 0));
            user.extinguish();
        }
        return result;
    }
}
