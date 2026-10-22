package net.unbeta.hg;

import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.PacketCallbacks;
import net.minecraft.network.packet.Packet;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/** A connection to nobody: a dummy player's "network". Everything sent to it is dropped. */
public final class DummyConnection extends ClientConnection {

    public DummyConnection() {
        super(NetworkSide.SERVERBOUND);
    }

    @Override
    public void send(Packet<?> packet) {
    }

    @Override
    public void send(Packet<?> packet, @Nullable PacketCallbacks callbacks) {
    }

    @Override
    public boolean isOpen() {
        return true;
    }

    @Override
    public void disconnect(Text reason) {
    }
}
