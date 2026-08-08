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
                if (!world.isClient) TorchItems.lightHeldTorch(held);
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });

        // Dual-wield: unlit in one hand + lit in the other -> light the unlit.
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            for (var player : world.getPlayers()) {
                ItemStack main = player.getMainHandStack();
                ItemStack off = player.getOffHandStack();
                boolean mainUnlit = TorchItems.isUnlitTorch(main);
                boolean offUnlit = TorchItems.isUnlitTorch(off);
                boolean mainLit = TorchItems.isTorchItem(main) && TorchItems.isLit(main);
                boolean offLit = TorchItems.isTorchItem(off) && TorchItems.isLit(off);
                if (mainUnlit && offLit) TorchItems.lightHeldTorch(main);
                else if (offUnlit && mainLit) TorchItems.lightHeldTorch(off);
            }
        });
    }
}
