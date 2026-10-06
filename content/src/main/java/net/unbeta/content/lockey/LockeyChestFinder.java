package net.unbeta.content.lockey;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.unbeta.core.state.UnbetaWorldState;

import java.util.UUID;

/**
 * Where a key's chest really is now - chests can be carried off, so the coordinates the
 * key stored when it locked may be stale.
 *
 * <ol>
 *   <li>Placed somewhere: the lock table names it by position. The key's stored
 *       coordinates are refreshed to match.</li>
 *   <li>Being carried: the carrying player's live position.</li>
 *   <li>Otherwise the coordinates the key last stored.</li>
 * </ol>
 */
public final class LockeyChestFinder {

    public record Where(BlockPos pos, boolean carried) {}

    private LockeyChestFinder() {}

    public static Where find(ServerWorld world, UUID lockId, ItemStack key) {
        if (lockId == null) return null;

        NbtCompound locks = UnbetaWorldState.read(world, "lockey");
        for (String k : locks.getKeys()) {
            if (!locks.containsUuid(k) || !lockId.equals(locks.getUuid(k))) continue;
            try {
                BlockPos pos = BlockPos.fromLong(Long.parseLong(k));
                if (!pos.equals(LockeyItem.getBoundChest(key))) LockeyItem.bind(key, pos);
                return new Where(pos, false);
            } catch (NumberFormatException ignored) {
            }
        }

        PlayerEntity carrier = CarryOnCompat.carrierOf(world.getServer(), lockId);
        if (carrier != null) return new Where(carrier.getBlockPos(), true);

        BlockPos stored = LockeyItem.getBoundChest(key);
        return stored == null ? null : new Where(stored, false);
    }
}
