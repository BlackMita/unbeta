package net.unbeta.content.lockey;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;

/**
 * Breaking into a locked chest by force.
 *
 * <p>A locked chest holding more than one item cannot be broken: the break is cancelled
 * and exactly one unit is forced out instead. Only when a single unit remains does the
 * break succeed, dropping that last item plus a plain unlocked chest.
 *
 * <p>Deliberately expensive - a full double chest is thousands of swings. Forcing a lock
 * should never be quicker than finding the key.
 */
public final class LockeySiege {

    private LockeySiege() {}

    /** Total item units across the whole container, counting both halves of a double. */
    private static int countUnits(Inventory inv) {
        int total = 0;
        for (int i = 0; i < inv.size(); i++) {
            total += inv.getStack(i).getCount();
        }
        return total;
    }

    /** The container for this chest position, merged across both halves if double. */
    private static Inventory inventoryAt(ServerWorld world, BlockPos pos) {
        if (!(world.getBlockState(pos).getBlock()
                instanceof net.minecraft.block.ChestBlock chest)) return null;
        return net.minecraft.block.ChestBlock.getInventory(
                chest, world.getBlockState(pos), world, pos, true);
    }

    /** Pull exactly one unit out of the first non-empty slot and drop it at the chest. */
    private static boolean ejectOneUnit(ServerWorld world, BlockPos pos, Inventory inv) {
        for (int i = 0; i < inv.size(); i++) {
            ItemStack slot = inv.getStack(i);
            if (slot.isEmpty()) continue;
            ItemStack one = inv.removeStack(i, 1);
            if (one.isEmpty()) continue;
            inv.markDirty();
            ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 1.0,
                    pos.getZ() + 0.5, one);
            return true;
        }
        return false;
    }

    /**
     * Called from PlayerBlockBreakEvents.BEFORE.
     *
     * @return true to allow the break, false to cancel it (one unit having been ejected)
     */
    public static boolean onBreak(ServerWorld world, PlayerEntity player, BlockPos pos) {
        if (!LockeyState.isChestLocked(world, pos)) return true; // not ours

        Inventory inv = inventoryAt(world, pos);
        if (inv == null) return true;

        int units = countUnits(inv);

        // Empty, or down to the final unit: let the break through. The chest drops its
        // remaining contents normally, and LockeyBreak handles revoking the key.
        if (units <= 1) return true;

        // More than one unit left: cancel, eject a single unit, chest survives.
        ejectOneUnit(world, pos, inv);
        world.playSound(null, pos,
                net.minecraft.sound.SoundEvents.BLOCK_CHEST_LOCKED,
                net.minecraft.sound.SoundCategory.BLOCKS, 0.4F, 1.6F);
        return false;
    }
}
