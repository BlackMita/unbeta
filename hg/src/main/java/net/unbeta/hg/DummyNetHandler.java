package net.unbeta.hg;

import net.minecraft.network.ClientConnection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/** A dummy's side of the conversation. It can't be kicked or time out: dummies leave via /hg dummy remove. */
public final class DummyNetHandler extends ServerPlayNetworkHandler {

    public DummyNetHandler(MinecraftServer server, ClientConnection connection, ServerPlayerEntity player) {
        super(server, connection, player);
    }

    @Override
    public void disconnect(Text reason) {
    }
}
