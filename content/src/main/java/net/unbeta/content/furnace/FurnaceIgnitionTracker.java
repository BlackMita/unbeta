package net.unbeta.content.furnace;

import net.minecraft.util.math.BlockPos;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class FurnaceIgnitionTracker {
    public static final Set<Long> IGNITED = Collections.synchronizedSet(new HashSet<>());
    private FurnaceIgnitionTracker() {}
    public static void ignite(BlockPos pos) { IGNITED.add(pos.asLong()); }
    public static boolean isIgnited(BlockPos pos) { return IGNITED.contains(pos.asLong()); }
}
