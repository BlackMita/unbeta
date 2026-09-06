package net.unbeta.content.jackolantern;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;

/**
 * Inventory/dropped-item burnout for lit Jack o'Lantern ITEMS (mirrors TorchLightingHooks).
 * A lit JoL item that passes its burnout deadline reverts to an unlit JoL.
 *
 * Only UNLIT JoLs are wearable, so a lit JoL should never be in the head slot — but we
 * still scan the full inventory (which includes armor slots) for safety.
 */
public final class JackOLanternItemBurnout {

    private JackOLanternItemBurnout() {}

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            long now = world.getTime();
            for (var player : world.getPlayers()) {
                PlayerInventory inv = player.getInventory();
                for (int i = 0; i < inv.size(); i++) {
                    ItemStack stack = inv.getStack(i);
                    if (!JackOLanternItems.isLit(stack)) continue;
                    long burnoutAt = JackOLanternItems.getBurnoutAt(stack);
                    if (burnoutAt < 0) {
                        // Lit with no deadline (edge case): give it one
                        inv.setStack(i, JackOLanternItems.createLitFromBlock(
                                now + JackOLanternLogic.FULL_BURN_TICKS,
                                JackOLanternLogic.FULL_BURN_TICKS));
                    } else if (now >= burnoutAt) {
                        inv.setStack(i, JackOLanternItems.createUnlit());
                    }
                }

                // Dropped lit JoLs that expired → fresh unlit entity (same dynamic-lights
                // caching issue as torches: must spawn a new entity, not swap the stack).
                world.getEntitiesByClass(ItemEntity.class,
                        player.getBoundingBox().expand(16),
                        e -> JackOLanternItems.isLit(e.getStack())).forEach(e -> {
                    long burnoutAt = JackOLanternItems.getBurnoutAt(e.getStack());
                    if (burnoutAt >= 0 && now >= burnoutAt) {
                        ItemEntity fresh = new ItemEntity(
                                world, e.getX(), e.getY(), e.getZ(),
                                JackOLanternItems.createUnlit(),
                                e.getVelocity().x, e.getVelocity().y, e.getVelocity().z);
                        world.spawnEntity(fresh);
                        e.discard();
                    }
                });
            }
        });
    }
}
