package net.unbeta.core.sched;

import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Handler fired when an item's countdown reaches zero.
 *
 * <p>Crucially it receives WHERE the stack currently is - the holder's position, or the
 * block position of the container holding it. That is what makes location-aware effects
 * possible: a creeper bomb sitting in a chest can explode AT THE CHEST, and a burnt-out
 * torch knows its own position.
 *
 * @param holder the entity holding the stack, or null if it is in a block container
 * @param pos    where the stack is right now (entity pos rounded, or container pos)
 * @return true to CONSUME the stack (remove it), false to leave it in place
 */
@FunctionalInterface
public interface ItemCountdownCallback {
    boolean onExpire(ServerWorld world, ItemStack stack, net.minecraft.entity.Entity holder, BlockPos pos);
}
