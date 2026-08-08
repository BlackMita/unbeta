package net.unbeta.content.torch;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.AbstractFireBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

/**
 * Two extra lighting paths from the spec:
 * <ul>
 *   <li><b>Fire block:</b> right-clicking any fire block while holding an unlit torch
 *       lights the held torch. (UseBlockCallback.)</li>
 *   <li><b>Dual-wield:</b> holding an unlit torch in one hand and a LIT torch in the
 *       other lights the unlit one. (Checked each server tick, cheaply, per player.)</li>
 * </ul>
 */
public final class TorchLightingHooks {

    private TorchLightingHooks() {}

    public static void register() {
        // Fire block -> light held unlit torch.
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            ItemStack held = player.getStackInHand(hand);
            if (!TorchItems.isUnlitTorch(held)) return ActionResult.PASS;
            var state = world.getBlockState(hit.getBlockPos());
            if (state.getBlock() instanceof AbstractFireBlock) {
                if (!world.isClient) TorchItems.lightHeldTorch(held, world.getTime());
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });

        // Dual-wield: unlit in one hand + lit in the other -> light the unlit.
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            long now = world.getTime();
            for (var player : world.getPlayers()) {
                ItemStack main = player.getMainHandStack();
                ItemStack off = player.getOffHandStack();

                // Burnout check (timestamp model - no per-second NBT writes, so no item "dip").
                burnoutTick(main, now);
                burnoutTick(off, now);
                boolean mainUnlit = TorchItems.isUnlitTorch(main);
                boolean offUnlit = TorchItems.isUnlitTorch(off);
                boolean mainLit = TorchItems.isTorchItem(main) && TorchItems.isLit(main);
                boolean offLit = TorchItems.isTorchItem(off) && TorchItems.isLit(off);
                if (mainUnlit && offLit) TorchItems.lightHeldTorch(main, now);
                else if (offUnlit && mainLit) TorchItems.lightHeldTorch(off, now);
            }
        });
    }

    /**
     * Burnout check for a held torch. Under the new model a LIT torch always carries an
     * absolute NBT_BURNOUT_AT, so we NEVER re-anchor it here (re-anchoring on pickup was
     * what silently refilled torches). We only check whether the deadline has passed.
     */
    private static void burnoutTick(ItemStack stack, long now) {
        if (!TorchItems.isTorchItem(stack) || !TorchItems.isLit(stack)) return;
        long burnoutAt = TorchItems.getBurnoutAt(stack);
        if (burnoutAt < 0) {
            // Lit but no deadline (legacy/edge): anchor once from remaining, then leave alone.
            TorchItems.lightHeldTorch(stack, now);
            return;
        }
        if (now >= burnoutAt) {
            TorchItems.extinguishHeldTorch(stack, now); // goes unlit, remaining resets for relight
        }
    }
}
