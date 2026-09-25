package net.unbeta.content.unmason;

import net.minecraft.registry.tag.StructureTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared "should this be an Unmason instead of a zombie" decision, used by BOTH the
 * global zombie-swap listener (for zombies that spawn some other way) and
 * SulliedChunkTick (for zombies rising out of a sullied chunk).
 *
 * <p>The stronghold cache lives here, not as a closure-local in each call site, so
 * there's exactly one 5-minute-refreshed lookup shared by both paths. Two independent
 * caches would drift out of sync with each other over time.
 */
public final class UnmasonOdds {

    private static final Map<ChunkPos, BlockPos> STRONGHOLD_CACHE = new ConcurrentHashMap<>();
    private static final long[] LAST_CACHE_TIME = {0};
    private static final ChunkPos CACHE_KEY = new ChunkPos(0, 0); // one shared entry, one world

    private UnmasonOdds() {}

    /** True if a mob spawning at this position should be an Unmason rather than a zombie. */
    public static boolean rollUnmason(ServerWorld world, BlockPos pos) {
        long now = world.getTime();
        if (!STRONGHOLD_CACHE.containsKey(CACHE_KEY) || now - LAST_CACHE_TIME[0] > 6000) {
            BlockPos found = world.locateStructure(StructureTags.EYE_OF_ENDER_LOCATED, pos, 100, false);
            if (found != null) STRONGHOLD_CACHE.put(CACHE_KEY, found);
            LAST_CACHE_TIME[0] = now;
        }

        BlockPos stronghold = STRONGHOLD_CACHE.get(CACHE_KEY);
        double dist = stronghold != null
                ? Math.sqrt(pos.getSquaredDistance(stronghold))
                : Double.MAX_VALUE;

        int chance;
        if (dist > 900) chance = 30;
        else if (dist > 450) chance = 10;
        else chance = 4;

        return world.getRandom().nextInt(chance) == 0;
    }
}
