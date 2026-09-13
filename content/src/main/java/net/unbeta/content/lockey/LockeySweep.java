package net.unbeta.content.lockey;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Keeps last-known Lockey positions fresh.
 *
 * <p>Recording only when someone clicks a locked chest leaves a dead spot: a key can ride
 * around for an hour with its stored position never updating, so the one time it's asked
 * for the answer is badly stale. This walks loaded keys every 10 seconds instead.
 *
 * <p>Only BOUND keys are recorded - an unbound Lockey has no chest to report to, so
 * tracking it would be pure cost.
 */
public final class LockeySweep {

    private static final int INTERVAL = 200; // ticks; 10 seconds

    private LockeySweep() {}

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(LockeySweep::tick);
    }

    private static void tick(ServerWorld world) {
        if (world.getTime() % INTERVAL != 0) return;

        for (PlayerEntity p : world.getPlayers()) {
            recordFrom(world, p.getInventory(), p.getBlockPos());
        }

        for (net.minecraft.entity.Entity e : world.iterateEntities()) {
            if (e instanceof ItemEntity ie) {
                record(world, ie.getStack(), ie.getBlockPos());
            } else if (e instanceof LivingEntity le && !(le instanceof PlayerEntity)) {
                for (net.minecraft.entity.EquipmentSlot slot
                        : net.minecraft.entity.EquipmentSlot.values()) {
                    record(world, le.getEquippedStack(slot), le.getBlockPos());
                }
            }
        }

        for (net.minecraft.block.entity.BlockEntity be : LoadedBlockEntities.iterate(world)) {
            if (be instanceof Inventory inv) recordFrom(world, inv, be.getPos());
        }
    }

    private static void recordFrom(Inventory inv, BlockPos pos) { /* unused overload guard */ }

    private static void recordFrom(ServerWorld world, Inventory inv, BlockPos pos) {
        for (int i = 0; i < inv.size(); i++) record(world, inv.getStack(i), pos);
    }

    private static void record(ServerWorld world, ItemStack stack, BlockPos pos) {
        if (!LockeyItem.isLockey(stack)) return;
        if (!LockeyItem.isBound(stack)) return;
        java.util.UUID id = LockeyItem.getId(stack);
        if (id != null) LockeyState.recordSeen(world, id, pos);
    }
}
