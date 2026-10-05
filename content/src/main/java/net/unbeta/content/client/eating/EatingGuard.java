package net.unbeta.content.client.eating;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.UseAction;

/**
 * Finishing a food or drink while still holding right-click used to fall straight through
 * to the off hand - placing whatever block was there. After eating or drinking, off-hand
 * use is blocked for at least MIN_TICKS AND until right-click is let go. The main hand is
 * never blocked, so holding right-click to eat several foods in a row still works.
 */
public final class EatingGuard {

    private static final int MIN_TICKS = 7; // about a third of a second

    private static boolean wasEating = false;
    private static int lockTicks = 0;
    private static boolean waitForRelease = false;

    private EatingGuard() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(EatingGuard::tick);
    }

    private static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            wasEating = false;
            lockTicks = 0;
            waitForRelease = false;
            return;
        }
        boolean held = client.options.useKey.isPressed();
        UseAction action = player.isUsingItem() ? player.getActiveItem().getUseAction() : UseAction.NONE;
        boolean eating = action == UseAction.EAT || action == UseAction.DRINK;

        // Just finished (not cancelled - right-click is still held): lock the off hand.
        if (wasEating && !eating && held) {
            lockTicks = MIN_TICKS;
            waitForRelease = true;
        }
        wasEating = eating;
        if (lockTicks > 0) lockTicks--;
        if (!held) waitForRelease = false;
    }

    public static boolean offhandLocked() {
        return lockTicks > 0 || waitForRelease;
    }
}
