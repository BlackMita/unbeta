package net.unbeta.content.bucket;

import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

/** Ice Scream: what Spoiled Ice Milk freezes into. See IceScream for what eating it does. */
public class IceScreamItem extends Item {

    public IceScreamItem(Settings settings) {
        super(settings);
    }

    @Override
    public Text getName(ItemStack stack) {
        return super.getName(stack).copy().formatted(Formatting.DARK_GREEN);
    }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        ItemStack result = super.finishUsing(stack, world, user);
        if (world instanceof ServerWorld sw) IceScream.eaten(sw, user);
        return result;
    }
}
