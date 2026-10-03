package net.unbeta.content.client.stronghold;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.unbeta.content.stronghold.StrongholdHudSync;

/**
 * TEMPORARY, for testing the stronghold metric: draws the server's reading just above the
 * hotbar - green inside a piece, yellow in the doorway margin, nothing elsewhere.
 */
public final class StrongholdHudOverlay {

    private static volatile String text = "";

    private StrongholdHudOverlay() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(StrongholdHudSync.CHANNEL,
                (client, handler, buf, responseSender) -> {
                    String s = buf.readString(128);
                    client.execute(() -> text = s);
                });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> text = "");

        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            String s = text;
            if (s.isEmpty()) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.options.hudHidden || client.player == null) return;
            int colour = s.startsWith("INSIDE") ? 0x55FF55 : 0xFFFF55;
            context.drawCenteredTextWithShadow(client.textRenderer, s,
                    context.getScaledWindowWidth() / 2,
                    context.getScaledWindowHeight() - 60, colour);
        });
    }
}
