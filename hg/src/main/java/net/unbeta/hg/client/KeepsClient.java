package net.unbeta.hg.client;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** What the server last told us about our keeps. */
public final class KeepsClient {

    private KeepsClient() {}

    public record Entry(boolean locked, List<Item> members) {
        public Item head() {
            return members.get(0);
        }
    }

    /** True once a Hunger Games server has spoken to us this session. */
    public static boolean onHgServer;
    public static boolean canBuy;
    public static boolean lobby;
    public static int nextCost = 3;
    public static String status = "";
    public static List<Entry> entries = new ArrayList<>();
    private static Set<Item> kept = new HashSet<>();

    public static void update(boolean canBuy, boolean lobby, int nextCost, String status, List<Entry> entries) {
        onHgServer = true;
        KeepsClient.canBuy = canBuy;
        KeepsClient.lobby = lobby;
        KeepsClient.nextCost = nextCost;
        KeepsClient.status = status;
        KeepsClient.entries = entries;
        Set<Item> set = new HashSet<>();
        for (Entry e : entries) set.addAll(e.members);
        kept = set;
    }

    public static void reset() {
        onHgServer = false;
        entries = new ArrayList<>();
        kept = new HashSet<>();
    }

    public static boolean isKept(Item item) {
        return kept.contains(item);
    }

    public static Entry entryFor(Item item) {
        for (Entry e : entries) if (e.members.contains(item)) return e;
        return null;
    }

    static Item item(Identifier id) {
        return Registries.ITEM.get(id);
    }
}
