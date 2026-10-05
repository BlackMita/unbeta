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
 *
 * <p>The server finishes the meal and tells the client BETWEEN ticks, and input handling
 * (which can place the off-hand block) runs before a tick ends. Checking only at the end of
 * the tick therefore noticed the finished meal one step too late. So the state is refreshed
 * at the start of every tick, and again at the exact moment an off-hand use is attempted.
 */
public final class EatingGuard {

    private static final int MIN_TICKS = 7; // about a third of a second

    private static boolean wasEating = false;
    private static int lockTicks = 0;
    private static boolean waitForRelease = false;

    private EatingGuard() {}

    public static void register() {
        ClientTickEvents.START_CLIENT_TICK.register(EatingGuard::refresh);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            refresh(client);
            if (lockTicks > 0) lockTicks--;
        });
    }

    private static void refresh(MinecraftClient client) {
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
        if (!held) waitForRelease = false;
    }

    public static boolean offhandLocked() {
        refresh(MinecraftClient.getInstance()); // evaluated at the moment of the attempt
        return lockTicks > 0 || waitForRelease;
    }
}
