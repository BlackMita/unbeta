package net.unbeta.content.lockey;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.DoubleInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.server.world.ServerWorld;

/**
 * Bridges "an Inventory" back to "is that a locked chest?".
 *
 * <p>A DoubleInventory has no position of its own, so we unwrap it to its two halves
 * (fields opened by our access widener) and test each. Either half being locked means
 * the whole container is locked - consistent with lockChest writing both positions.
 */
public final class LockeyInventories {

    private LockeyInventories() {}

    public static boolean isLocked(Inventory inv) {
        if (inv == null) return false;

        if (inv instanceof DoubleInventory di) {
            return isLocked(di.first) || isLocked(di.second);
        }

        if (inv instanceof BlockEntity be) {
            if (!(be.getWorld() instanceof ServerWorld)) return false;
            ServerWorld sw = (ServerWorld) be.getWorld();
            return LockeyState.isLocked(sw, be.getPos());
        }

        return false;
    }
}
