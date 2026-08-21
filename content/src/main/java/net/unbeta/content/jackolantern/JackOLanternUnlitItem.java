package net.unbeta.content.jackolantern;

import net.minecraft.block.Block;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Equipment;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

/** Unlit Unbeta Jack o'Lantern — wearable in the head slot as a disguise. */
public class JackOLanternUnlitItem extends BlockItem implements Equipment {

    public JackOLanternUnlitItem(Block block, Settings settings) {
        super(block, settings);
    }

    @Override
    public EquipmentSlot getSlotType() {
        return EquipmentSlot.HEAD;
    }

    @Override
    public SoundEvent getEquipSound() {
        return SoundEvents.ITEM_ARMOR_EQUIP_GENERIC;
    }
}
