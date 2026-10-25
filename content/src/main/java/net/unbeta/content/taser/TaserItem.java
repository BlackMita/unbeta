package net.unbeta.content.taser;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

/**
 * The Taser. Right-click fires its darts (up to 12 blocks) into a mob, a player or a copper
 * block; right-click again lets go. Its durability bar is its battery: 2 minutes of stun.
 * A flat taser does nothing until it's crafted with redstone dust.
 */
public class TaserItem extends Item {

    public static final int BATTERY_TICKS = 2 * 60 * 20;

    public TaserItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient && user instanceof ServerPlayerEntity sp) Taser.toggle(sp, hand);
        return TypedActionResult.success(stack, world.isClient);
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.NONE;
    }

    @Override
    public boolean canRepair(ItemStack stack, ItemStack ingredient) {
        return false;   // recharged with redstone dust in the crafting grid instead
    }
}
