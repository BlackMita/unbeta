package net.unbeta.hg.bot;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What a bot remembers: marks for things it has seen (each with the time it was last seen, and
 * fading by kind, see the design doc) and sightings of players. Everything here came through its
 * senses; bots never share notebooks.
 */
public final class Notebook {

    /** Kinds of mark, how long each lasts (ticks, -1 = never) and how many it keeps (nearest win). */
    public enum Kind {
        // Resources: never forgotten unless found gone
        LOG("tree", -1, 24), ORE("ore", -1, 32), COBBLE("cobblestone", -1, 16), WATER("water", -1, 12),
        LAVA("lava", -1, 12), LEAVES("oak leaves", -1, 8), GRAVEL("gravel", -1, 8), PUMPKIN("pumpkin", -1, 6),
        // Structures: never
        TABLE("crafting table", -1, 8), FURNACE("furnace", -1, 8), CHEST("chest", -1, 16),
        CLAMBOX("clambox", -1, 8), DOOR("obsidian door", -1, 8), PLACED("someone's handiwork", 6000, 24),
        // Ways down: cave openings it has seen into, and rails (Unbeta's old minecart tracks run past ore)
        CAVE("cave", -1, 24), RAIL("rail", -1, 16),
        // Flames to light a torch from: lit furnaces and torches go out, so these fade
        FLAME("flame", 6000, 12),
        // Danger: 10 minutes; its own death spots never
        DANGER("danger", 12000, 16), DEATH("where I died", -1, 8);

        public final String label;
        public final int lasts;
        public final int cap;

        Kind(String label, int lasts, int cap) {
            this.label = label;
            this.lasts = lasts;
            this.cap = cap;
        }
    }

    public static final class Mark {
        public final Kind kind;
        public final BlockPos pos;
        public long seen;
        /** What exactly: "iron ore", "fire", "Guardian Post". */
        public String what;

        Mark(Kind kind, BlockPos pos, long seen, String what) {
            this.kind = kind;
            this.pos = pos;
            this.seen = seen;
            this.what = what;
        }
    }

    /** A player it has seen, and what it noticed about them. */
    public static final class Sighting {
        public final UUID id;
        public String name;
        public Vec3d pos;
        public String holding;
        public long seen;
        /** The flavour they were last seen showing (for Middling contagion), or null. */
        public Reaction.Tag showing;
        public Vec3d heading = Vec3d.ZERO;
        /** What they hold (the item), for Copycats. */
        public net.minecraft.item.Item holdingItem;
        /** Since when they've stayed within 24 blocks of the bot (being followed?), or -1. */
        public long closeSince = -1;
        /** Is it holding a weapon (sword, axe, bow)? */
        public boolean armed;

        Sighting(UUID id) {
            this.id = id;
        }
    }

    public static final int SIGHTING_LASTS = 2400;   // 2 minutes, then only "last seen"

    private final Map<Kind, LinkedHashMap<BlockPos, Mark>> marks = new EnumMap<>(Kind.class);
    public final Map<UUID, Sighting> sightings = new HashMap<>();
    /** Chests it has opened (it doesn't loot the same one twice). */
    public final java.util.Set<BlockPos> opened = new java.util.HashSet<>();
    /** Hits it has landed on each foe (its guess at their health). */
    public final Map<UUID, Integer> hits = new HashMap<>();
    /** Who it has fought, and when. */
    public final Map<UUID, Long> fought = new HashMap<>();
    /** Item kinds seen in other players' hands, and how often (Copycat). */
    public final Map<net.minecraft.item.Item, Integer> handsSeen = new HashMap<>();
    /** Piles left where someone it fought died: position and when. */
    public final Map<BlockPos, Long> piles = new HashMap<>();
    /** Its stash chest (Tortoise), if it has one. */
    public BlockPos stash;
    /** Who killed it, for the whole round. */
    public UUID grudge;
    public String grudgeName;

    public Notebook() {
        for (Kind k : Kind.values()) marks.put(k, new LinkedHashMap<>());
    }

    /** Notes a mark (or refreshes it). True if it was new. */
    public boolean note(Kind kind, BlockPos pos, long now, String what, BlockPos from) {
        LinkedHashMap<BlockPos, Mark> map = marks.get(kind);
        Mark m = map.get(pos);
        if (m != null) {
            m.seen = now;
            m.what = what;
            return false;
        }
        map.put(pos.toImmutable(), new Mark(kind, pos.toImmutable(), now, what));
        if (map.size() > kind.cap) {
            // Forget the farthest.
            BlockPos far = null;
            double farD = -1;
            for (BlockPos p : map.keySet()) {
                double d = p.getSquaredDistance(from);
                if (d > farD) {
                    farD = d;
                    far = p;
                }
            }
            map.remove(far);
        }
        return true;
    }

    public boolean has(Kind kind, BlockPos pos) {
        return marks.get(kind).containsKey(pos);
    }

    public void forget(Kind kind, BlockPos pos) {
        marks.get(kind).remove(pos);
    }

    /** The nearest mark of a kind within range, or null. */
    public Mark nearest(Kind kind, BlockPos from, double range) {
        Mark best = null;
        double bestD = range * range;
        for (Mark m : marks.get(kind).values()) {
            double d = m.pos.getSquaredDistance(from);
            if (d <= bestD) {
                bestD = d;
                best = m;
            }
        }
        return best;
    }

    /** The nearest mark of a kind within range whose label passes a test. */
    public Mark nearest(Kind kind, BlockPos from, double range, java.util.function.Predicate<Mark> test) {
        Mark best = null;
        double bestD = range * range;
        for (Mark m : marks.get(kind).values()) {
            if (!test.test(m)) continue;
            double d = m.pos.getSquaredDistance(from);
            if (d <= bestD) {
                bestD = d;
                best = m;
            }
        }
        return best;
    }

    public List<Mark> all(Kind kind) {
        return new ArrayList<>(marks.get(kind).values());
    }

    public Sighting sighting(UUID id) {
        return sightings.computeIfAbsent(id, Sighting::new);
    }

    /** Lets old marks fade. */
    public void fade(long now) {
        for (Kind k : Kind.values()) {
            if (k.lasts < 0) continue;
            marks.get(k).values().removeIf(m -> now - m.seen > k.lasts);
        }
    }

    /** Player sightings no older than 2 minutes. */
    public List<Sighting> recent(long now) {
        List<Sighting> out = new ArrayList<>();
        for (Sighting s : sightings.values()) if (now - s.seen <= SIGHTING_LASTS) out.add(s);
        return out;
    }

    /** Lines for /hg bot notebook. */
    public List<String> describe(long now, BlockPos from) {
        List<String> lines = new ArrayList<>();
        for (Kind k : Kind.values()) {
            Map<BlockPos, Mark> map = marks.get(k);
            if (map.isEmpty()) continue;
            Mark near = nearest(k, from, 100000);
            lines.add(map.size() + " x " + k.label + (near == null ? "" : " (nearest: " + near.what + " at "
                    + near.pos.toShortString() + ", " + age(now - near.seen) + " ago)"));
        }
        for (Iterator<Sighting> it = sightings.values().iterator(); it.hasNext(); ) {
            Sighting s = it.next();
            lines.add("saw " + s.name + " at " + BlockPos.ofFloored(s.pos).toShortString() + " holding " + s.holding
                    + ", " + age(now - s.seen) + " ago" + (now - s.seen > SIGHTING_LASTS ? " (last seen)" : ""));
        }
        if (grudge != null) lines.add("grudge: " + grudgeName);
        if (lines.isEmpty()) lines.add("(empty)");
        return lines;
    }

    static String age(long ticks) {
        long s = ticks / 20;
        return s < 60 ? s + "s" : (s / 60) + "m" + (s % 60) + "s";
    }
}
