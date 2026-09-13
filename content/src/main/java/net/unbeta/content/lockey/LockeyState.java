package net.unbeta.content.lockey;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.unbeta.core.state.UnbetaWorldState;

import java.util.UUID;

/**
 * Which chests are locked, and by which Lockey.
 *
 * <p>Stored in the shared UnbetaWorldState under the "lockey" section as a flat map of
 * chest position (packed long, as a string key) -> Lockey UUID. A chest with an entry is
 * locked; no entry means unlocked.
 *
 * <p>A chest whose Lockey no longer exists anywhere keeps its entry - that is the
 * "Will never unlock" case, and is deliberately indistinguishable here from a key that
 * merely sits in an unloaded chunk. Deciding between those is the searcher's job, not
 * this layer's.
 */
public final class LockeyState {

    private static final String SECTION = "lockey";

    private LockeyState() {}

    private static String key(BlockPos pos) {
        return Long.toString(pos.asLong());
    }

    public static boolean isLocked(ServerWorld world, BlockPos pos) {
        return UnbetaWorldState.read(world, SECTION).contains(key(pos));
    }

    /** The Lockey that locked this chest, or null if the chest is unlocked. */
    public static UUID lockedBy(ServerWorld world, BlockPos pos) {
        NbtCompound s = UnbetaWorldState.read(world, SECTION);
        String k = key(pos);
        return s.containsUuid(k) ? s.getUuid(k) : null;
    }

    public static void lock(ServerWorld world, BlockPos pos, UUID lockeyId) {
        UnbetaWorldState.edit(world, SECTION, s -> s.putUuid(key(pos), lockeyId));
    }

    public static void unlock(ServerWorld world, BlockPos pos) {
        UnbetaWorldState.edit(world, SECTION, s -> s.remove(key(pos)));
    }

    /** True if this specific Lockey is the one holding this chest. */
    public static boolean matches(ServerWorld world, BlockPos pos, UUID lockeyId) {
        UUID owner = lockedBy(world, pos);
        return owner != null && owner.equals(lockeyId);
    }
}
