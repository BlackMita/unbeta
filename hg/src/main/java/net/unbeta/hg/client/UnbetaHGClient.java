package net.unbeta.hg.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.Item;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.unbeta.hg.Keeps;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The player's side of keeps: K always opens the Keep screen on a Hunger Games server (it's
 * wired straight to the key, not a rebindable control), kept items say so in their tooltip, and
 * a big sign tells players about K at the start of every round (each round is a new connection)
 * until they've pressed it. Out players see everyone still in outlined through walls, and can
 * jump again in mid-air as often as they like.
 */
public final class UnbetaHGClient implements ClientModInitializer {

    /** K pressed since joining: the sign comes back each time you join (every round is a new world). */
    private static boolean seen;
    private static boolean kWasDown;
    private static boolean jumpWasDown;

    @Override
    public void onInitializeClient() {
        Halo.register();

        ClientPlayNetworking.registerGlobalReceiver(Keeps.SYNC, (client, handler, buf, sender) -> {
            boolean canBuy = buf.readBoolean();
            boolean lobby = buf.readBoolean();
            int nextCost = buf.readVarInt();
            String status = buf.readString();
            int n = buf.readVarInt();
            List<KeepsClient.Entry> entries = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                boolean locked = buf.readBoolean();
                int m = buf.readVarInt();
                List<Item> members = new ArrayList<>();
                for (int j = 0; j < m; j++) members.add(KeepsClient.item(buf.readIdentifier()));
                entries.add(new KeepsClient.Entry(locked, members));
            }
            client.execute(() -> KeepsClient.update(canBuy, lobby, nextCost, status, entries));
        });
        ClientPlayNetworking.registerGlobalReceiver(net.unbeta.hg.OutPlayers.SYNC, (client, handler, buf, sender) -> {
            int n = buf.readVarInt();
            java.util.Set<java.util.UUID> ids = new java.util.HashSet<>();
            for (int i = 0; i < n; i++) ids.add(buf.readUuid());
            client.execute(() -> OutClient.update(ids));
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> seen = false);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            seen = false;
            KeepsClient.reset();
            OutClient.reset();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Spectres jump on air: every press of Jump is a jump, landed or not.
            boolean jump = client.options.jumpKey.isPressed();
            if (jump && !jumpWasDown && client.player != null && client.currentScreen == null
                    && OutClient.isOut(client.player.getUuid()) && !client.player.isOnGround()
                    && !client.player.isTouchingWater() && !client.player.isClimbing()) {
                client.player.jump();
            }
            jumpWasDown = jump;

            boolean down = InputUtil.isKeyPressed(client.getWindow().getHandle(), GLFW.GLFW_KEY_K);
            boolean pressed = down && !kWasDown;
            kWasDown = down;
            // Only while playing (not typing in chat or another screen), and only on a Hunger Games server.
            if (!pressed || client.player == null || client.currentScreen != null || !KeepsClient.onHgServer) return;
            client.setScreen(new KeepScreen());
            seen = true;
        });

        HudRenderCallback.EVENT.register((ctx, tickDelta) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (seen || !KeepsClient.onHgServer || mc.player == null || mc.options.hudHidden) return;
            TextRenderer tr = mc.textRenderer;
            int w = ctx.getScaledWindowWidth();
            int y = 44;
            boolean flash = (System.currentTimeMillis() / 600) % 2 == 0;
            Text big = Text.literal("PRESS  K  FOR KEEPS").formatted(flash ? Formatting.GREEN : Formatting.YELLOW, Formatting.BOLD);
            Text small1 = Text.literal("Keeps are kinds of item you never lose when you die.").formatted(Formatting.WHITE);
            Text small2 = Text.literal("You start with 3 levels: enough for your first keep.").formatted(Formatting.GRAY);
            int bigW = tr.getWidth(big) * 3;
            int boxW = Math.max(bigW, Math.max(tr.getWidth(small1), tr.getWidth(small2))) + 20;
            ctx.fill(w / 2 - boxW / 2, y - 6, w / 2 + boxW / 2, y + 27 + 26, 0xB0000000);
            ctx.getMatrices().push();
            ctx.getMatrices().translate(w / 2F, y, 0F);
            ctx.getMatrices().scale(3F, 3F, 1F);
            ctx.drawTextWithShadow(tr, big, -tr.getWidth(big) / 2, 0, 0xFFFFFF);
            ctx.getMatrices().pop();
            ctx.drawCenteredTextWithShadow(tr, small1, w / 2, y + 30, 0xFFFFFF);
            ctx.drawCenteredTextWithShadow(tr, small2, w / 2, y + 41, 0xFFFFFF);
        });

        ItemTooltipCallback.EVENT.register((stack, context, lines) -> {
            if (KeepsClient.onHgServer && !stack.isEmpty() && KeepsClient.isKept(stack.getItem())) {
                lines.add(Math.min(1, lines.size()), Text.literal("Kept").formatted(Formatting.GREEN));
            }
        });
    }
}
