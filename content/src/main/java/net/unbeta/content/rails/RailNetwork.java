package net.unbeta.content.rails;

/**
 * Deterministic overworld rail network.
 *
 * <p>The world is divided into square cells of {@link #CELL_SIZE} blocks. Every cell
 * contains exactly one node, whose XZ position AND Y elevation are pure hashes of
 * (seed, cellX, cellZ). Each node may connect EAST and SOUTH to its neighbours - only
 * those two directions, so a link is never drawn twice.
 *
 * <p>Node elevation is terrain-INDEPENDENT: a node sits at its planned Y regardless of
 * the ground there. A segment's rail height interpolates linearly between its two nodes'
 * Y values. The terrain then conforms to that committed line - carved where ground rises
 * above it, filled where ground falls below it. The rail never reacts to terrain; terrain
 * reacts to the rail. (Later, node Y can be softened to respond to biome/terrain - but the
 * rigid version is built and proven first.)
 */
public final class RailNetwork {

    public static final int CELL_SIZE = 320;
    private static final int CELL_MARGIN = 80;
    private static final int CONNECT_CHANCE = 70;
    private static final int TUNNEL_CHANCE = 50;

    /** Core node elevation tiers. Nodes snap to one of these Y values.
     *  63 = sea level, 71 = mid-hill, 79 = high plateau. Adjacent tiers differ by 8,
     *  which over a 320-block segment produces a gentle "long slope" grade.
     *  Later: extend downward (55, 47, 39, ...) for underground rail layers. */
    private static final int[] NODE_Y_TIERS = {56, 64, 72, 80};

    private RailNetwork() {}

    private static long hash(long seed, int cx, int cz, int salt) {
        long h = seed;
        h = h * 6364136223846793005L + (cx * 341873128712L);
        h = h * 6364136223846793005L + (cz * 132897987541L);
        h = h * 6364136223846793005L + (salt * 2654435761L);
        h ^= (h >>> 33); h *= 0xff51afd7ed558ccdL;
        h ^= (h >>> 33); h *= 0xc4ceb9fe1a85ec53L;
        h ^= (h >>> 33);
        return h;
    }

    private static int hashRange(long seed, int cx, int cz, int salt, int bound) {
        return (int) Math.floorMod(hash(seed, cx, cz, salt), bound);
    }

    public static int nodeX(long seed, int cx, int cz) {
        int span = CELL_SIZE - CELL_MARGIN * 2;
        return cx * CELL_SIZE + CELL_MARGIN + hashRange(seed, cx, cz, 1, span);
    }

    public static int nodeZ(long seed, int cx, int cz) {
        int span = CELL_SIZE - CELL_MARGIN * 2;
        return cz * CELL_SIZE + CELL_MARGIN + hashRange(seed, cx, cz, 2, span);
    }

    /** Planned, terrain-independent elevation of this node (snapped to a core tier). */
    public static int nodeY(long seed, int cx, int cz) {
        return NODE_Y_TIERS[hashRange(seed, cx, cz, 5, NODE_Y_TIERS.length)];
    }

    public static final int DIR_EAST = 10;
    public static final int DIR_SOUTH = 20;

    public static boolean hasConnection(long seed, int cx, int cz, int dir) {
        return hashRange(seed, cx, cz, dir, 100) < CONNECT_CHANCE;
    }

    public static boolean isTunnel(long seed, int cx, int cz, int dir) {
        return hashRange(seed, cx, cz, dir + 1, 100) < TUNNEL_CHANCE;
    }

    public static long segmentSeed(long seed, int cx, int cz, int dir) {
        return hash(seed, cx, cz, dir + 3);
    }

    public static int cellOf(int blockCoord) {
        return Math.floorDiv(blockCoord, CELL_SIZE);
    }
}
