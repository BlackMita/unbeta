package net.unbeta.content.heatediron;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/**
 * A heated iron block in item form. It keeps cooling (its heat bar shows what's left) and turns
 * back into plain iron in your inventory when it's cold. Held, it burns: half a heart every 2
 * seconds (Fire Resistance protects).
 */
public class HeatedIronItem extends BlockItem {

    public HeatedIronItem(Block block, Settings settings) {
        super(block, settings);
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (world.isClient) return;
        if (HeatedIron.cold(stack, world)) {
            if (entity instanceof PlayerEntity p) {
                if (p.getOffHandStack() == stack) p.setStackInHand(net.minecraft.util.Hand.OFF_HAND, HeatedIron.cooled(stack));
                else p.getInventory().setStack(slot, HeatedIron.cooled(stack));
            }
            return;
        }
        if (entity instanceof LivingEntity living && (selected || living.getOffHandStack() == stack) && world.getTime() % 40 == 0) {
            living.damage(world.getDamageSources().hotFloor(), 0.5F);   // doubled as fire: half a heart
        }
    }

    @Override
    public boolean isItemBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getItemBarStep(ItemStack stack) {
        return Math.round(13.0F * HeatBar.fraction(stack));
    }

    @Override
    public int getItemBarColor(ItemStack stack) {
        return 0xFF8A1C;
    }
}
