package net.unbeta.hg;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Adds and removes dummy players (/hg dummy add | remove). */
public final class Dummies {

    private static final String[] NAMES = {"Dummy_Ash", "Dummy_Bram", "Dummy_Cole", "Dummy_Dell", "Dummy_Eve",
            "Dummy_Finn", "Dummy_Gus", "Dummy_Hale", "Dummy_Ivy", "Dummy_Jett", "Dummy_Kit", "Dummy_Lux",
            "Dummy_Moe", "Dummy_Nix"};

    private Dummies() {}

    public static void register() {
        ServerLifecycleEvents.SERVER_STOPPING.register(Dummies::removeAll);
    }

    public static List<DummyPlayer> all(MinecraftServer server) {
        List<DummyPlayer> list = new ArrayList<>();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) if (p instanceof DummyPlayer d) list.add(d);
        return list;
    }

    /** Adds up to n dummies (names not already in use). Returns how many joined. */
    public static int add(MinecraftServer server, int n) {
        int added = 0;
        for (String name : NAMES) {
            if (added >= n) break;
            if (server.getPlayerManager().getPlayer(name) != null) continue;
            GameProfile profile = new GameProfile(
                    UUID.nameUUIDFromBytes(("unbeta-dummy:" + name).getBytes(StandardCharsets.UTF_8)), name);
            DummyPlayer dummy = new DummyPlayer(server, server.getOverworld(), profile);
            DummyConnection connection = new DummyConnection();
            server.getPlayerManager().onPlayerConnect(connection, dummy);
            new DummyNetHandler(server, connection, dummy);
            added++;
        }
        return added;
    }

    public static int removeAll(MinecraftServer server) {
        List<DummyPlayer> list = all(server);
        for (DummyPlayer d : list) server.getPlayerManager().remove(d);
        return list.size();
    }
}
