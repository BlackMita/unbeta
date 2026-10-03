package net.unbeta.content.stronghold;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePiece;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * TEMPORARY, for testing the stronghold metric: once a second, tells each player whether
 * they're inside the stronghold and which piece, for the on-screen readout
 * (StrongholdHudOverlay). Only the server has the structure data, hence the message.
 *
 * <p>To remove later: delete this class, StrongholdHudOverlay, and the one registration
 * line in UnbetaContent and UnbetaContentClient.
 */
public final class StrongholdHudSync {

    public static final Identifier CHANNEL = new Identifier("unbeta-content", "stronghold_hud");

    private StrongholdHudSync() {}

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(StrongholdHudSync::tick);
    }

    private static void tick(ServerWorld world) {
        if (world.getTime() % 20 != 0) return;
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (!ServerPlayNetworking.canSend(player, CHANNEL)) continue;
            BlockPos pos = player.getBlockPos();
            StructurePiece piece = StrongholdSpace.pieceAt(world, pos);
            String text;
            if (piece != null) {
                text = "INSIDE stronghold - " + piece.getType();
            } else if (StrongholdSpace.startAt(world, pos) != null) {
                text = "Just outside a piece (doorway margin)";
            } else {
                text = "";  // not in a stronghold: the overlay draws nothing
            }
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeString(text, 128);
            ServerPlayNetworking.send(player, CHANNEL, buf);
        }
    }
}
