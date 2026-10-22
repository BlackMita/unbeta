package net.unbeta.hg;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameRules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps: bought with levels, each one protects a family of items from your deaths. Every keep
 * costs 3 levels, whatever level you're at. In the lobby a pick can be changed freely
 * (the levels come back); when the countdown starts every pick locks in, and from then on any
 * pick locks in at once. Everyone gets 3 levels once per round, when they first arrive - never
 * again (respawning gives nothing), so the only way to more keeps is earning experience.
 */
public final class Keeps {

    private Keeps() {}

    public static final Identifier SYNC = new Identifier(UnbetaHG.MOD_ID, "keeps");
    public static final Identifier PICK = new Identifier(UnbetaHG.MOD_ID, "pick");
    public static final Identifier UNPICK = new Identifier(UnbetaHG.MOD_ID, "unpick");

    public static final int STEP = 3;
    private static final int START_LEVELS = 3;

    private static final class Keep {
        final Item head;
        final List<Item> members;
        boolean locked;
        final int paid;

        Keep(Item head, List<Item> members, boolean locked, int paid) {
            this.head = head;
            this.members = members;
            this.locked = locked;
            this.paid = paid;
        }
    }

    private record Stashed(int slot, ItemStack stack) {}

    private static final Map<UUID, List<Keep>> keeps = new HashMap<>();
    private static final Map<UUID, List<Stashed>> stash = new HashMap<>();
    private static final Set<UUID> granted = new HashSet<>();

    /** What the next keep costs (always 3 levels). */
    public static int cost(int owned) {
        return STEP;
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(PICK, (server, player, handler, buf, sender) -> {
            Identifier id = buf.readIdentifier();
            server.execute(() -> pick(server, player, id));
        });
        ServerPlayNetworking.registerGlobalReceiver(UNPICK, (server, player, handler, buf, sender) -> {
            Identifier id = buf.readIdentifier();
            server.execute(() -> unpick(player, id));
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sync(handler.player));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> restore(newPlayer));
    }

    private static List<Keep> of(ServerPlayerEntity p) {
        return keeps.computeIfAbsent(p.getUuid(), k -> new ArrayList<>());
    }

    public static boolean kept(ServerPlayerEntity p, Item item) {
        List<Keep> list = keeps.get(p.getUuid());
        if (list == null) return false;
        for (Keep k : list) if (k.members.contains(item)) return true;
        return false;
    }

    /** Your 3 starting levels, once per round. */
    public static void grant(ServerPlayerEntity p) {
        if (!granted.add(p.getUuid())) return;
        p.setExperienceLevel(START_LEVELS);
        p.setExperiencePoints(0);
    }

    // ------------------------------------------------------------------ picking

    private static void pick(MinecraftServer server, ServerPlayerEntity p, Identifier id) {
        if (!Registries.ITEM.containsId(id)) return;
        Item item = Registries.ITEM.get(id);
        if (item == Items.AIR) return;
        String refusal = refusal(p);
        if (refusal != null) {
            tell(p, refusal, Formatting.GRAY);
            return;
        }
        if (kept(p, item)) {
            tell(p, "You already keep that.", Formatting.GRAY);
            return;
        }
        List<Keep> list = of(p);
        boolean lobby = Round.phase() == Round.Phase.LOBBY;

        // In the lobby, a pick you can't afford swaps out your latest unlocked one.
        Keep swapped = null;
        int cost = cost(list.size());
        if (lobby && p.experienceLevel < cost) {
            for (int i = list.size() - 1; i >= 0; i--) {
                Keep k = list.get(i);
                if (!k.locked) {
                    if (p.experienceLevel + k.paid >= cost(list.size() - 1)) swapped = k;
                    break;
                }
            }
            if (swapped != null) {
                list.remove(swapped);
                p.addExperienceLevels(swapped.paid);
                cost = cost(list.size());
            }
        }
        if (p.experienceLevel < cost) {
            tell(p, "That keep costs " + cost + " levels; you have " + p.experienceLevel + ".", Formatting.RED);
            return;
        }
        p.addExperienceLevels(-cost);
        List<Item> members = Families.of(item);
        list.add(new Keep(members.get(0), members, !lobby, cost));
        String name = name(members.get(0));
        String msg = "Keeping " + name + (swapped != null ? " instead of " + name(swapped.head) : "")
                + (lobby ? " - you can change it until the countdown starts." : " - locked in for the round.");
        tell(p, msg, Formatting.GREEN);
        p.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 0.6F, 1.6F);
        sync(p);
    }

    /** Lobby only: give back a pick that isn't locked in yet. */
    private static void unpick(ServerPlayerEntity p, Identifier id) {
        if (Round.phase() != Round.Phase.LOBBY || !Registries.ITEM.containsId(id)) return;
        Item item = Registries.ITEM.get(id);
        List<Keep> list = of(p);
        for (Keep k : list) {
            if (!k.locked && k.members.contains(item)) {
                list.remove(k);
                p.addExperienceLevels(k.paid);
                tell(p, "No longer keeping " + name(k.head) + " (levels back).", Formatting.GRAY);
                p.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.MASTER, 0.6F, 0.8F);
                sync(p);
                return;
            }
        }
    }

    /** Why this player can't buy a keep right now, or null if they can. */
    private static String refusal(ServerPlayerEntity p) {
        Round.Phase ph = Round.phase();
        if (ph == Round.Phase.ENDED) return "The round is over.";
        if (Round.isOut(p)) return "You're out of this round.";
        if ((ph == Round.Phase.GRACE || ph == Round.Phase.GAME) && !Round.tookPart(p.getUuid())) return "You're watching this round.";
        return null;
    }

    /** The countdown has started: every pick so far is final. */
    public static void lockAll(MinecraftServer server) {
        for (List<Keep> list : keeps.values()) for (Keep k : list) k.locked = true;
        syncAll(server);
    }

    // ------------------------------------------------------------------ deaths

    /** Called as a player's inventory is about to drop: kept items are set aside instead. */
    public static void stashKept(ServerPlayerEntity p) {
        if (p.getWorld().getGameRules().getBoolean(GameRules.KEEP_INVENTORY)) return;
        if (!keeps.containsKey(p.getUuid())) return;
        PlayerInventory inv = p.getInventory();
        List<Stashed> saved = stash.computeIfAbsent(p.getUuid(), k -> new ArrayList<>());
        for (int slot = 0; slot < inv.size(); slot++) {
            ItemStack s = inv.getStack(slot);
            if (!s.isEmpty() && kept(p, s.getItem())) {
                saved.add(new Stashed(slot, s.copy()));
                inv.setStack(slot, ItemStack.EMPTY);
            }
        }
    }

    /** Back with you after you respawn, in the same slots where they can be. */
    public static void restore(ServerPlayerEntity p) {
        List<Stashed> saved = stash.remove(p.getUuid());
        if (saved == null) return;
        PlayerInventory inv = p.getInventory();
        for (Stashed s : saved) {
            if (s.slot < inv.size() && inv.getStack(s.slot).isEmpty()) inv.setStack(s.slot, s.stack);
            else inv.offerOrDrop(s.stack);
        }
    }

    // ------------------------------------------------------------------ telling the client

    public static void syncAll(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) sync(p);
    }

    /** Sends a player their keeps, the next price and what they may do right now (for the Keep screen). */
    public static void sync(ServerPlayerEntity p) {
        if (p instanceof DummyPlayer || !ServerPlayNetworking.canSend(p, SYNC)) return;
        List<Keep> list = keeps.getOrDefault(p.getUuid(), List.of());
        PacketByteBuf buf = PacketByteBufs.create();
        String refusal = refusal(p);
        boolean lobby = Round.phase() == Round.Phase.LOBBY;
        buf.writeBoolean(refusal == null);
        buf.writeBoolean(lobby);
        buf.writeVarInt(cost(list.size()));
        buf.writeString(refusal != null ? refusal
                : lobby ? "Lobby: change your keep freely until the countdown starts."
                : "A keep bought now locks in at once.");
        buf.writeVarInt(list.size());
        for (Keep k : list) {
            buf.writeBoolean(k.locked);
            buf.writeVarInt(k.members.size());
            for (Item i : k.members) buf.writeIdentifier(Registries.ITEM.getId(i));
        }
        ServerPlayNetworking.send(p, SYNC, buf);
    }

    private static String name(Item item) {
        return item.getName().getString();
    }

    private static void tell(ServerPlayerEntity p, String text, Formatting colour) {
        p.sendMessage(Text.literal(text).formatted(colour));
    }
}
