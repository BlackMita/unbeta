package net.unbeta.hg;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * One round, start to finish. Phases:
 * <ol>
 *   <li>LOBBY - everyone restricted (walk and jump only, no harm). Lasts at most 5 minutes with
 *       fewer than 4 players, 4 with 4-7, 3 with 8 or more; it waits on if too few have come.</li>
 *   <li>COUNTDOWN - everyone pulled to spawn and frozen for 10 seconds.</li>
 *   <li>GRACE - free and unhurtable for 1 minute. The 45-minute game clock starts here.</li>
 *   <li>GAME - open play until one player is left in, or the clock runs out. Each death costs a
 *       max heart (the Attrition datapack); when the last one goes, or you surrender, you're
 *       out. Leaving mid-round counts as a death where you stand; you can rejoin and play on, and
 *       you count as in while you're away.</li>
 *   <li>ENDED - the winner is announced; 30 seconds later the server stops and start.sh starts
 *       it again on a brand-new world.</li>
 * </ol>
 * Days and nights run twice as fast from GRACE on. The world border is x and z +-999 around spawn.
 */
public final class Round {

    public enum Phase { LOBBY, COUNTDOWN, GRACE, GAME, ENDED }

    public static final int BORDER = 999;
    private static final int COUNTDOWN_TICKS = 10 * 20;
    private static final int GRACE_TICKS = 60 * 20;
    private static final int GAME_TICKS = 45 * 60 * 20;          // counted from the start of GRACE
    private static final int END_TICKS = 30 * 20;
    /** start.sh restarts the server on a new world when it finds this file. */
    public static final String NEXT_ROUND_FILE = ".hg-next-round";

    private static Phase phase = Phase.LOBBY;
    private static int ticks;                       // ticks in the current phase
    private static int clock;                       // ticks since GRACE began
    private static int startedWith;                 // players in when GRACE began
    private static boolean testMode;
    private static final Set<UUID> out = new HashSet<>();
    private static final Map<UUID, Vec3d> frozenAt = new HashMap<>();
    /** Everyone who took part this round, by name (for players who are offline). */
    private static final Map<UUID, String> names = new HashMap<>();
    /** Max health (half-hearts) of players in the round who are offline, after the heart leaving cost them. */
    private static final Map<UUID, Float> awayHealth = new HashMap<>();
    /** Attrition tags a player whose last heart is gone (and makes them a spectator). */
    private static final String SPIRIT_TAG = "mp.attr.spirit";
    private static ServerBossBar bar;
    private static final Random RANDOM = new Random();

    private Round() {}

    public static Phase phase() {
        return phase;
    }

    /** Ticks left on the round's clock (from the start of the grace minute). */
    public static int ticksLeft() {
        return Math.max(0, GAME_TICKS - clock);
    }

    /** How many players were in when the grace minute began. */
    public static int startedWith() {
        return startedWith;
    }

    public static boolean testMode() {
        return testMode;
    }

    public static void setTestMode(boolean on) {
        testMode = on;
    }

    /** Restricted: walk and jump only, can't touch anything, can't be hurt or hurt anyone. */
    public static boolean restricted(PlayerEntity player) {
        return phase == Phase.LOBBY || phase == Phase.COUNTDOWN || phase == Phase.ENDED || out.contains(player.getUuid());
    }

    /** No one can be hurt during the lobby, countdown, grace minute or after the end. */
    public static boolean unhurtable(PlayerEntity player) {
        return restricted(player) || phase == Phase.GRACE;
    }

    public static Set<UUID> outPlayers() {
        return out;
    }

    public static boolean tookPart(UUID id) {
        return tookPart.contains(id);
    }

    public static boolean isOut(PlayerEntity player) {
        return out.contains(player.getUuid());
    }

    public static void register() {
        bar = new ServerBossBar(Text.literal("Lobby"), BossBar.Color.WHITE, BossBar.Style.PROGRESS);
        ServerLifecycleEvents.SERVER_STARTED.register(Round::setUpWorld);
        ServerTickEvents.END_SERVER_TICK.register(Round::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> joined(server, handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> left(server, handler.player));
    }

    // ------------------------------------------------------------------ world

    private static void setUpWorld(MinecraftServer server) {
        ServerWorld world = server.getOverworld();
        BlockPos spawn = world.getSpawnPos();
        world.getWorldBorder().setCenter(spawn.getX() + 0.5, spawn.getZ() + 0.5);
        world.getWorldBorder().setSize(BORDER * 2 + 1);
        server.getGameRules().get(GameRules.SPAWN_RADIUS).set(0, server);
        // Sleeping never skips the night (beds still set your respawn point).
        server.getGameRules().get(GameRules.PLAYERS_SLEEPING_PERCENTAGE).set(101, server);
        phase = Phase.LOBBY;
        ticks = 0;
        UnbetaHG.LOG.info("Round ready: lobby open, border +-{} around {}", BORDER, spawn.toShortString());
    }

    private static void joined(MinecraftServer server, ServerPlayerEntity player) {
        bar.addPlayer(player);
        awayHealth.remove(player.getUuid());
        if (phase == Phase.GRACE || phase == Phase.GAME) {
            // Too late to play this round: watch instead.
            if (!out.contains(player.getUuid()) && !wasIn(player)) {
                out.add(player.getUuid());
                player.sendMessage(Text.literal("A round is under way - you're watching this one.").formatted(Formatting.GRAY));
            } else if (wasIn(player) && !out.contains(player.getUuid())) {
                player.sendMessage(Text.literal("Welcome back. Leaving cost you a heart; you're still in.").formatted(Formatting.YELLOW));
            }
        }
        if (restricted(player)) restrict(player);
    }

    private static final Set<UUID> tookPart = new HashSet<>();

    private static boolean wasIn(ServerPlayerEntity player) {
        return tookPart.contains(player.getUuid());
    }

    /**
     * Leaving mid-round is a death where you stand: your things and experience drop there and
     * the heart goes when you respawn (Attrition counts it then). Leaving on your last heart puts
     * you out at once.
     */
    private static void left(MinecraftServer server, ServerPlayerEntity p) {
        bar.removePlayer(p);
        if (p instanceof DummyPlayer) return;
        UUID id = p.getUuid();
        if ((phase != Phase.GRACE && phase != Phase.GAME) || !tookPart.contains(id) || out.contains(id)) return;
        names.put(id, p.getName().getString());
        float max = p.getMaxHealth();
        awayHealth.put(id, Math.max(0F, max - 2F));
        if (!p.isDead()) {
            broadcast(server, Text.literal(p.getName().getString() + " left mid-round and dropped where they stood.").formatted(Formatting.GRAY));
            p.setHealth(0F);
            p.onDeath(p.getDamageSources().generic());
        }
        if (max <= 2F) putOut(server, id, "left on their last heart");
    }

    // ------------------------------------------------------------------ ticking

    private static void tick(MinecraftServer server) {
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        ticks++;
        // Attrition's "has surrendered a heart of maximum health!" lines are muted on this server:
        // its own text-off tag. (They read too much like someone surrendering the round.)
        if (ticks % 20 == 0) for (ServerPlayerEntity p : players) p.addCommandTag("mp.attr.fixed_off");
        // Attrition's last heart: out of the round, in our restricted mode rather than its spectator mode.
        for (ServerPlayerEntity p : new ArrayList<>(players)) {
            if (p.getCommandTags().contains(SPIRIT_TAG)) {
                p.removeScoreboardTag(SPIRIT_TAG);
                if (tookPart.contains(p.getUuid()) && !out.contains(p.getUuid())) putOut(server, p.getUuid(), "no hearts left");
                else restrict(p);
            }
        }
        OutPlayers.tick(server);
        switch (phase) {
            case LOBBY -> lobby(server, players);
            case COUNTDOWN -> countdown(server, players);
            case GRACE, GAME -> game(server, players);
            case ENDED -> ended(server, players);
        }
    }

    private static int lobbyLimit(int count) {
        if (count < 4) return 5 * 60 * 20;
        if (count < 8) return 4 * 60 * 20;
        return 3 * 60 * 20;
    }

    private static int minPlayers() {
        return testMode ? 1 : 2;
    }

    private static void lobby(MinecraftServer server, List<ServerPlayerEntity> players) {
        for (ServerPlayerEntity p : players) {
            if (p.interactionManager.getGameMode() != GameMode.ADVENTURE && !p.hasPermissionLevel(2)) restrict(p);
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SATURATION, 40, 0, true, false));
            Keeps.grant(p);
        }
        int limit = lobbyLimit(players.size());
        if (players.size() < minPlayers()) {
            bar.setName(Text.literal("Lobby - waiting for players (" + players.size() + "/" + minPlayers() + ")"));
            bar.setPercent(1.0F);
            if (ticks > limit) ticks = limit;   // starts as soon as enough arrive
            return;
        }
        int left = Math.max(0, limit - ticks);
        bar.setName(Text.literal("Lobby - the game starts in " + time(left)));
        bar.setPercent(Math.max(0F, Math.min(1F, left / (float) limit)));
        if (left <= 0) startCountdown(server);
    }

    /** /hg start, or the lobby running out: everyone to spawn, frozen, 10 seconds. */
    public static void startCountdown(MinecraftServer server) {
        if (phase != Phase.LOBBY) return;
        phase = Phase.COUNTDOWN;
        ticks = 0;
        frozenAt.clear();
        ServerWorld world = server.getOverworld();
        BlockPos spawn = world.getSpawnPos();
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        for (int i = 0; i < players.size(); i++) {
            ServerPlayerEntity p = players.get(i);
            double a = Math.PI * 2 * i / Math.max(1, players.size());
            double r = players.size() > 1 ? 3.0 : 0.0;
            double x = spawn.getX() + 0.5 + Math.cos(a) * r, z = spawn.getZ() + 0.5 + Math.sin(a) * r;
            double y = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
            float yaw = (float) Math.toDegrees(a) + 90F;
            p.teleport(world, x, y, z, yaw, 0F);
            frozenAt.put(p.getUuid(), new Vec3d(x, y, z));
        }
        broadcast(server, Text.literal("The game is about to begin. Everyone is held at spawn. Keeps are now locked in.").formatted(Formatting.GOLD));
        Keeps.lockAll(server);
    }

    private static void countdown(MinecraftServer server, List<ServerPlayerEntity> players) {
        for (ServerPlayerEntity p : players) {
            Keeps.grant(p);
            Vec3d at = frozenAt.get(p.getUuid());
            if (at != null && p.getPos().squaredDistanceTo(at) > 0.01) {
                p.teleport(server.getOverworld(), at.x, at.y, at.z, p.getYaw(), p.getPitch());
            }
        }
        int left = COUNTDOWN_TICKS - ticks;
        bar.setName(Text.literal("Get ready..."));
        bar.setPercent(Math.max(0F, left / (float) COUNTDOWN_TICKS));
        if (left % 20 == 0 && left > 0) {
            int s = left / 20;
            for (ServerPlayerEntity p : players) {
                title(p, Text.literal(String.valueOf(s)).formatted(s <= 3 ? Formatting.RED : Formatting.GOLD), Text.empty());
                p.playSound(SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), SoundCategory.MASTER, 1.0F, s <= 3 ? 1.4F : 1.0F);
            }
        }
        if (left <= 0) startGrace(server, players);
    }

    private static void startGrace(MinecraftServer server, List<ServerPlayerEntity> players) {
        phase = Phase.GRACE;
        ticks = 0;
        clock = 0;
        out.clear();
        tookPart.clear();
        names.clear();
        awayHealth.clear();
        server.getOverworld().setTimeOfDay(0);
        for (ServerPlayerEntity p : players) {
            p.changeGameMode(GameMode.SURVIVAL);
            p.getInventory().clear();
            p.setHealth(p.getMaxHealth());
            p.getHungerManager().setFoodLevel(20);
            p.getHungerManager().setSaturationLevel(5.0F);
            p.clearStatusEffects();
            Keeps.grant(p);        // (levels left after lobby keeps are yours to keep or spend)
            tookPart.add(p.getUuid());
            names.put(p.getUuid(), p.getName().getString());
            title(p, Text.literal("GO!").formatted(Formatting.GREEN), Text.literal("1 minute of safety - gear up").formatted(Formatting.GRAY));
            p.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 1.0F, 1.0F);
        }
        startedWith = players.size();
        Keeps.syncAll(server);
        broadcast(server, Text.literal("Go! You can't be hurt for 1 minute. Last one standing wins.").formatted(Formatting.GREEN));
    }

    private static void game(MinecraftServer server, List<ServerPlayerEntity> players) {
        clock++;
        // Days and nights at double speed.
        ServerWorld world = server.getOverworld();
        if (world.getGameRules().getBoolean(GameRules.DO_DAYLIGHT_CYCLE)) world.setTimeOfDay(world.getTimeOfDay() + 1);

        if (phase == Phase.GRACE && ticks >= GRACE_TICKS) {
            phase = Phase.GAME;
            ticks = 0;
            broadcast(server, Text.literal("The grace minute is over. Fight!").formatted(Formatting.RED));
            for (ServerPlayerEntity p : players) p.playSound(SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.MASTER, 0.4F, 1.4F);
        }
        int left = Math.max(0, GAME_TICKS - clock);
        List<UUID> still = stillIn();
        if (phase == Phase.GRACE) {
            // The grace minute has its own light-blue bar...
            int safe = Math.max(0, GRACE_TICKS - ticks);
            bar.setName(Text.literal("Grace - safe for " + time(safe) + "  |  " + still.size() + " in"));
            bar.setPercent(safe / (float) GRACE_TICKS);
            bar.setColor(BossBar.Color.BLUE);
        } else {
            // ...then a yellow one for the remaining 44 minutes.
            bar.setName(Text.literal("Time left " + time(left) + "  |  " + still.size() + " in"));
            bar.setPercent(Math.min(1F, left / (float) (GAME_TICKS - GRACE_TICKS)));
            bar.setColor(BossBar.Color.YELLOW);
        }

        if (startedWith >= 2 && still.size() == 1) {
            end(server, names.get(still.get(0)), "last one standing");
        } else if (still.isEmpty()) {
            end(server, null, "nobody left");
        } else if (left <= 0) {
            List<Standing> table = standings(server);
            broadcast(server, Text.literal("Time's up! Final standings:").formatted(Formatting.GOLD));
            for (int i = 0; i < table.size(); i++) {
                Standing s = table.get(i);
                broadcast(server, Text.literal("  " + (i + 1) + ". " + s.name + " - " + hearts(s.maxHealth)
                        + ", " + s.xp + " experience").formatted(i == 0 ? Formatting.YELLOW : Formatting.GRAY));
            }
            end(server, table.get(0).name, "most hearts when time ran out");
        }
    }

    /** Everyone still in the round, online or not. */
    public static List<UUID> stillIn() {
        List<UUID> list = new ArrayList<>();
        for (UUID id : tookPart) if (!out.contains(id)) list.add(id);
        return list;
    }

    /** Players online and still in the round. */
    public static List<ServerPlayerEntity> in(List<ServerPlayerEntity> players) {
        List<ServerPlayerEntity> list = new ArrayList<>();
        for (ServerPlayerEntity p : players) if (tookPart.contains(p.getUuid()) && !out.contains(p.getUuid())) list.add(p);
        return list;
    }

    private record Standing(UUID id, String name, float maxHealth, int xp, int dice) {}

    /** Most max hearts, then most experience, then a dice roll. Away players count with what leaving left them. */
    private static List<Standing> standings(MinecraftServer server) {
        List<Standing> list = new ArrayList<>();
        for (UUID id : stillIn()) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            String name = names.getOrDefault(id, "?");
            if (p != null) {
                // On the death screen the heart isn't gone yet, but it will be.
                float max = p.isDead() ? Math.max(0F, p.getMaxHealth() - 2F) : p.getMaxHealth();
                list.add(new Standing(id, name, max, p.isDead() ? 0 : points(p), RANDOM.nextInt()));
            } else {
                list.add(new Standing(id, name, awayHealth.getOrDefault(id, 0F), 0, RANDOM.nextInt()));
            }
        }
        list.sort(Comparator.comparingDouble((Standing s) -> -s.maxHealth)
                .thenComparingInt(s -> -s.xp)
                .thenComparingInt(s -> s.dice));
        return list;
    }

    private static String hearts(float maxHealth) {
        int h = Math.round(maxHealth / 2F);
        return h + (h == 1 ? " heart" : " hearts");
    }

    private static int points(ServerPlayerEntity p) {
        return net.unbeta.content.obsidiandoor.XpHelper.totalPoints(p);
    }

    /** Puts a player out of the round (hearts gone, or surrendered). */
    public static void putOut(MinecraftServer server, ServerPlayerEntity player, String why) {
        names.putIfAbsent(player.getUuid(), player.getName().getString());
        putOut(server, player.getUuid(), why);
    }

    public static void putOut(MinecraftServer server, UUID id, String why) {
        if (phase != Phase.GRACE && phase != Phase.GAME) return;
        if (!out.add(id)) return;
        String name = names.getOrDefault(id, "Someone");
        ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
        if (p != null) {
            restrict(p);
            title(p, Text.literal("You're out").formatted(Formatting.RED),
                    Text.literal("Walk about and watch - you can't touch anything").formatted(Formatting.GRAY));
        }
        if (p != null) Keeps.sync(p);
        OutPlayers.syncAll(server);
        OutPlayers.makeSpectre(server, name);
        int left = stillIn().size();
        broadcast(server, Text.literal(name + " is out (" + why + "). " + left + (left == 1 ? " player" : " players") + " left.")
                .formatted(Formatting.RED));
        for (ServerPlayerEntity q : server.getPlayerManager().getPlayerList()) {
            q.playSound(SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.MASTER, 0.5F, 1.0F);
        }
    }

    /** /surrender confirm: out of the round at once. */
    public static String surrender(MinecraftServer server, ServerPlayerEntity player) {
        if (phase != Phase.GRACE && phase != Phase.GAME) return "You can only surrender while a round is being played.";
        if (!tookPart.contains(player.getUuid())) return "You're not playing this round.";
        if (out.contains(player.getUuid())) return "You're already out.";
        // Surrendering leaves everything behind, keeps included, right where you stand.
        player.getInventory().dropAll();
        putOut(server, player, "surrendered");
        return null;
    }

    /** /hg end, or the game being decided. */
    public static void end(MinecraftServer server, String winner, String how) {
        if (phase == Phase.ENDED) return;
        phase = Phase.ENDED;
        ticks = 0;
        Text title = winner == null ? Text.literal("No winner").formatted(Formatting.GRAY)
                : Text.literal(winner + " wins!").formatted(Formatting.GOLD);
        Text sub = Text.literal(winner == null ? "" : "(" + how + ")").formatted(Formatting.GRAY);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            title(p, title, sub);
            p.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 1.0F, 1.0F);
            restrict(p);
        }
        broadcast(server, title.copy().append(Text.literal(" A new round on a new world starts in 30 seconds.").formatted(Formatting.GRAY)));
        Keeps.syncAll(server);
        bar.setName(Text.literal("Round over"));
        bar.setPercent(0F);
    }

    private static void ended(MinecraftServer server, List<ServerPlayerEntity> players) {
        if (ticks < END_TICKS) return;
        if (ticks == END_TICKS) {
            try {
                new File(server.getRunDirectory(), NEXT_ROUND_FILE).createNewFile();
            } catch (IOException e) {
                UnbetaHG.LOG.error("Couldn't mark the next round", e);
            }
            Dummies.removeAll(server);
            UnbetaHG.LOG.info("Round over: stopping for a new world.");
            server.stop(false);
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Adventure mode (no breaking or placing), unhurtable, never hungry. Everything else is blocked in Restrictions. */
    public static void restrict(ServerPlayerEntity p) {
        if (p.hasPermissionLevel(2) && phase == Phase.LOBBY && p.interactionManager.getGameMode() == GameMode.CREATIVE) return;
        p.changeGameMode(GameMode.ADVENTURE);
        p.extinguish();
        // (A player on the death screen stays dead: they'll respawn into the restricted mode.)
        if (!p.isDead()) p.setHealth(Math.max(p.getHealth(), 1.0F));
    }

    private static void title(ServerPlayerEntity p, Text title, Text subtitle) {
        p.networkHandler.sendPacket(new TitleFadeS2CPacket(0, 30, 10));
        p.networkHandler.sendPacket(new SubtitleS2CPacket(subtitle));
        p.networkHandler.sendPacket(new TitleS2CPacket(title));
    }

    private static void broadcast(MinecraftServer server, Text text) {
        server.getPlayerManager().broadcast(text, false);
    }

    private static String time(int ticks) {
        int s = Math.max(0, ticks) / 20;
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    public static String status(MinecraftServer server) {
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        return "Phase " + phase + ", " + time(ticks) + " in it; game clock " + time(clock) + "; "
                + stillIn().size() + " in (" + in(players).size() + " online), " + out.size() + " out; test mode "
                + (testMode ? "on" : "off") + hp(players);
    }

    private static String hp(List<ServerPlayerEntity> players) {
        StringBuilder b = new StringBuilder();
        for (ServerPlayerEntity p : players) {
            b.append("\n  ").append(p.getName().getString()).append(": ").append(hearts(p.getMaxHealth()))
                    .append(", level ").append(p.experienceLevel).append(out.contains(p.getUuid()) ? " (out)" : "");
        }
        return b.toString();
    }
}
