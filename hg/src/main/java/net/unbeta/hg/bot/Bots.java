package net.unbeta.hg.bot;

import com.mojang.authlib.GameProfile;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.unbeta.hg.DummyConnection;
import net.unbeta.hg.DummyNetHandler;
import net.unbeta.hg.DummyPlayer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Adds bots: dummies with a body and three traits. Each gets a short name (Minecraft caps names
 * at 16 letters) and its traits after it on its nametag and in the tab list ("Maple the Middling
 * Survivor Hog"), via a one-player team. No two bots in a round share all three traits.
 */
public final class Bots {

    private Bots() {}

    private static final String[] NAMES = {"Maple", "Rook", "Wren", "Bramble", "Tansy", "Pike", "Juniper", "Flint",
            "Moss", "Sorrel", "Quill", "Hazel", "Basil", "Cinder", "Fern", "Thistle", "Clover", "Sable", "Birch", "Lark"};
    private static final Random RANDOM = new Random();

    public static List<Body> all(MinecraftServer server) {
        List<Body> list = new ArrayList<>();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p instanceof DummyPlayer d && d.body != null) list.add(d.body);
        }
        return list;
    }

    /** Adds up to n bots, with traits from the words given (the rest random). Returns their names and titles. */
    public static List<String> add(MinecraftServer server, int n, String traitWords) {
        Set<Traits> taken = new HashSet<>();
        for (Body b : all(server)) taken.add(b.traits);
        List<String> added = new ArrayList<>();
        List<String> names = new ArrayList<>(List.of(NAMES));
        java.util.Collections.shuffle(names, RANDOM);
        for (String name : names) {
            if (added.size() >= n) break;
            if (server.getPlayerManager().getPlayer(name) != null) continue;
            Traits traits = Traits.roll(traitWords, taken, RANDOM);
            if (traits == null) break;
            taken.add(traits);
            GameProfile profile = new GameProfile(
                    UUID.nameUUIDFromBytes(("unbeta-bot:" + name).getBytes(StandardCharsets.UTF_8)), name);
            DummyPlayer bot = new DummyPlayer(server, server.getOverworld(), profile);
            bot.body = new Body(bot, traits);
            DummyConnection connection = new DummyConnection();
            server.getPlayerManager().onPlayerConnect(connection, bot);
            new DummyNetHandler(server, connection, bot);
            title(server, name, traits);
            added.add(name + " " + traits.title());
        }
        return added;
    }

    /** /hg bot brains on|off: every bot's brain at once. */
    public static boolean brainsOn = true;

    /** Shows what a bot is doing after its traits on its nametag: "the Middling Survivor Hog · S1 On fire 3s". */
    public static void status(net.unbeta.hg.DummyPlayer bot, String doing) {
        String name = bot.getName().getString();
        Team team = bot.getServer().getScoreboard().getTeam("hgbot_" + name.toLowerCase());
        if (team == null || bot.body == null) return;
        team.setSuffix(Text.literal(" " + bot.body.traits.title()).formatted(Formatting.GRAY)
                .append(Text.literal(" · " + doing).formatted(Formatting.YELLOW)));
    }

    /** "Name the Middling Survivor Hog" on its nametag and in the tab list. */
    private static void title(MinecraftServer server, String name, Traits traits) {
        ServerScoreboard board = server.getScoreboard();
        String teamName = "hgbot_" + name.toLowerCase();
        Team team = board.getTeam(teamName);
        if (team == null) team = board.addTeam(teamName);
        team.setSuffix(Text.literal(" " + traits.title()).formatted(Formatting.GRAY));
        board.addPlayerToTeam(name, team);
    }
}
