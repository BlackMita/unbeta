package net.unbeta.content.unlikelike;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * The theft cascade. Called at spit-out moment.
 *
 * Priority:
 * 1. Shield (offhand)
 * 2. Random equipped armor piece
 * 3. Random item from hotbar
 * 4. Random item from main inventory
 * 5. Nothing to steal → double damage + "Your pride has been stolen."
 */
public final class UnlikeLikeTheft {

    private UnlikeLikeTheft() {}

    public static ItemStack stealFrom(PlayerEntity player) {
        // 1. Shield in offhand
        ItemStack offhand = player.getEquippedStack(EquipmentSlot.OFFHAND);
        if (offhand.isOf(Items.SHIELD)) {
            player.equipStack(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            sendMessage(player, offhand.getName().getString() + " has been stolen!");
            return offhand;
        }

        // 2. Random armor piece
        EquipmentSlot[] armorSlots = {
                EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET
        };
        List<EquipmentSlot> wornArmor = new ArrayList<>();
        for (EquipmentSlot slot : armorSlots) {
            if (!player.getEquippedStack(slot).isEmpty()) wornArmor.add(slot);
        }
        if (!wornArmor.isEmpty()) {
            EquipmentSlot slot = wornArmor.get(player.getRandom().nextInt(wornArmor.size()));
            ItemStack armor = player.getEquippedStack(slot).copy();
            player.equipStack(slot, ItemStack.EMPTY);
            sendMessage(player, armor.getName().getString() + " has been stolen!");
            return armor;
        }

        // 3. Random hotbar item
        PlayerInventory inv = player.getInventory();
        List<Integer> hotbarSlots = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            if (!inv.getStack(i).isEmpty()) hotbarSlots.add(i);
        }
        if (!hotbarSlots.isEmpty()) {
            int slot = hotbarSlots.get(player.getRandom().nextInt(hotbarSlots.size()));
            ItemStack item = inv.getStack(slot).copy();
            inv.setStack(slot, ItemStack.EMPTY);
            sendMessage(player, item.getName().getString() + " has been stolen!");
            return item;
        }

        // 4. Random main inventory item
        List<Integer> invSlots = new ArrayList<>();
        for (int i = 9; i < inv.size(); i++) {
            if (!inv.getStack(i).isEmpty()) invSlots.add(i);
        }
        if (!invSlots.isEmpty()) {
            int slot = invSlots.get(player.getRandom().nextInt(invSlots.size()));
            ItemStack item = inv.getStack(slot).copy();
            inv.setStack(slot, ItemStack.EMPTY);
            sendMessage(player, item.getName().getString() + " has been stolen!");
            return item;
        }

        // 5. Nothing to steal
        player.damage(player.getWorld().getDamageSources().generic(), 4.0F); // double spit damage
        sendMessage(player, "Your pride has been stolen.");
        return ItemStack.EMPTY;
    }

    private static void sendMessage(PlayerEntity player, String msg) {
        // false = chat message (more reliable than action bar for server-side)
        player.sendMessage(Text.literal("§c" + msg), false);
    }
}
