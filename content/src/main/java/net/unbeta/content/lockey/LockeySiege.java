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

    /**
     * Pull exactly one unit out of a randomly chosen slot and drop it at the chest.
     *
     * <p>Weighted by unit count, not by slot: a stack of 63 dirt sitting beside 1 diamond
     * means the diamond has a 1-in-64 chance per swing, not a 1-in-2 chance. This is what
     * makes "bury the valuable slot under full junk stacks" an actual defence - every
     * extra unit of filler dilutes the odds of any single swing hitting the good slot,
     * rather than the old (broken) behaviour of draining slots strictly in order.
     *
     * <p>Re-rolled fresh every single swing against the inventory's CURRENT contents -
     * no memory of "which slot we're draining." Whichever slot the roll lands on this
     * time is independent of every previous swing.
     */
    private static boolean ejectOneUnit(ServerWorld world, BlockPos pos, Inventory inv) {
        int total = countUnits(inv);
        if (total <= 0) return false;

        int roll = world.getRandom().nextInt(total); // 0..total-1
        int cursor = 0;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack slot = inv.getStack(i);
            int count = slot.getCount();
            if (count <= 0) continue;
            cursor += count;
            if (roll < cursor) {
                // This slot owns the rolled unit.
                ItemStack one = inv.removeStack(i, 1);
                if (one.isEmpty()) return false; // shouldn't happen, but stay safe
                inv.markDirty();
                ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 1.0,
                        pos.getZ() + 0.5, one);
                return true;
            }
        }
        return false; // shouldn't be reachable if total > 0, but stay safe
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
