package net.unbeta.content.torch;

import net.minecraft.item.ItemStack;
import net.unbeta.core.sched.UnbetaScheduler;

/**
 * Registers the scheduler ITEM-countdown handler for held/stored lit torches. When the
 * countdown expires the torch reverts to unlit (not consumed - reusable), matching the
 * placed-torch burnout. Registered once at content init.
 */
public final class UnbetaTorchItemBurnout {

    public static final String HANDLER_ID = "unbeta:torch_item_burnout";

    private UnbetaTorchItemBurnout() {}

    public static void register() {
        UnbetaScheduler.registerItemHandler(HANDLER_ID, (world, stack, holder, pos) -> {
            // Revert to unlit; keep the stack (reusable).
            if (stack.hasNbt() && stack.getNbt() != null) {
                stack.getNbt().putBoolean(TorchItems.NBT_LIT, false);
            }
            return false; // do not consume
        });
    }

    /** Stamp the scheduler item-countdown onto a torch stack. */
    public static void stamp(ItemStack stack, long ticks) {
        UnbetaScheduler.setItemCountdown(stack, ticks, HANDLER_ID);
    }
}
