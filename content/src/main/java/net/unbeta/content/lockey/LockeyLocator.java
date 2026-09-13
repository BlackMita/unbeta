package net.unbeta.content.lockey;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

/**
 * Finds where a given Lockey physically is, right now.
 *
 * <p>Searches, in cheapest-first order: player inventories, loaded dropped items, loaded
 * living entities' equipment, then loaded container block entities.
 *
 * <p>A null result means "not found in anything currently loaded" - NOT "destroyed".
 * Those two are different claims and only one of them is safe to tell the player.
 */
public final class LockeyLocator {

    private LockeyLocator() {}

    /** Where this Lockey is, or null if it isn't in anything currently loaded. */
    public static BlockPos find(ServerWorld world, UUID lockeyId) {
        if (lockeyId == null) return null;

        // 1. Player inventories (includes offhand and armor slots)
        for (PlayerEntity p : world.getPlayers()) {
            if (containsLockey(p.getInventory(), lockeyId)) return p.getBlockPos();
        }

        // 2. Dropped items and mob-held stacks among loaded entities
        for (net.minecraft.entity.Entity e : world.iterateEntities()) {
            if (e instanceof ItemEntity ie) {
                if (matches(ie.getStack(), lockeyId)) return ie.getBlockPos();
            } else if (e instanceof LivingEntity le) {
                for (net.minecraft.entity.EquipmentSlot slot
                        : net.minecraft.entity.EquipmentSlot.values()) {
                    if (matches(le.getEquippedStack(slot), lockeyId)) return le.getBlockPos();
                }
            }
        }

        // 3. Loaded container block entities (chests, barrels, hoppers, droppers...)
        for (BlockEntity be : LoadedBlockEntities.iterate(world)) {
            if (be instanceof Inventory inv && containsLockey(inv, lockeyId)) {
                return be.getPos();
            }
        }

        return null;
    }

    private static boolean matches(ItemStack stack, UUID lockeyId) {
        if (!LockeyItem.isLockey(stack)) return false;
        UUID id = LockeyItem.getId(stack);
        return id != null && id.equals(lockeyId);
    }

    private static boolean containsLockey(Inventory inv, UUID lockeyId) {
        for (int i = 0; i < inv.size(); i++) {
            if (matches(inv.getStack(i), lockeyId)) return true;
        }
        return false;
    }
}
