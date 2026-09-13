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

    /**
     * The other half of a double chest, or null for a single chest.
     *
     * <p>Uses vanilla's own ChestBlock.getFacing, which returns the direction pointing
     * at the partner half (LEFT rotates clockwise, RIGHT counter-clockwise). Deriving
     * this by hand would break on some orientations.
     */
    public static BlockPos otherHalf(ServerWorld world, BlockPos pos) {
        net.minecraft.block.BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof net.minecraft.block.ChestBlock)) return null;
        if (state.get(net.minecraft.block.ChestBlock.CHEST_TYPE)
                == net.minecraft.block.enums.ChestType.SINGLE) return null;
        return pos.offset(net.minecraft.block.ChestBlock.getFacing(state));
    }

    /**
     * Lock a chest, covering both halves if it is a double chest. Downstream checks
     * (deny, hoppers, pistons, explosions) then only ever need a per-position lookup.
     */
    public static void lockChest(ServerWorld world, BlockPos pos, UUID lockeyId) {
        lock(world, pos, lockeyId);
        BlockPos other = otherHalf(world, pos);
        if (other != null) lock(world, other, lockeyId);
    }

    /** Unlock a chest and its partner half, if any. */
    public static void unlockChest(ServerWorld world, BlockPos pos) {
        BlockPos other = otherHalf(world, pos);
        unlock(world, pos);
        if (other != null) unlock(world, other);
    }

    /**
     * Is this chest locked, counting its partner half?
     *
     * <p>Guards against a locked single chest being merged into a double chest by a
     * newly placed neighbour: the new half has no lock of its own, so testing only the
     * clicked position would let it be opened.
     */
    public static boolean isChestLocked(ServerWorld world, BlockPos pos) {
        if (isLocked(world, pos)) return true;
        BlockPos other = otherHalf(world, pos);
        return other != null && isLocked(world, other);
    }

    /** The Lockey holding this chest or its partner half, or null if neither is locked. */
    public static UUID chestLockedBy(ServerWorld world, BlockPos pos) {
        UUID owner = lockedBy(world, pos);
        if (owner != null) return owner;
        BlockPos other = otherHalf(world, pos);
        return other != null ? lockedBy(world, other) : null;
    }

    // ---- last-known key positions, and revoked keys ----

    private static final String SEEN = "lockey_seen";
    private static final String DEAD = "lockey_dead";

    /** Remember where this key was seen, so a later failed search still has an answer. */
    public static void recordSeen(ServerWorld world, UUID lockeyId, BlockPos pos) {
        if (lockeyId == null || pos == null) return;
        UnbetaWorldState.edit(world, SEEN, s -> s.putLong(lockeyId.toString(), pos.asLong()));
    }

    /** Where this key was last seen, or null if we have never found it. */
    public static BlockPos lastSeen(ServerWorld world, UUID lockeyId) {
        if (lockeyId == null) return null;
        NbtCompound s = UnbetaWorldState.read(world, SEEN);
        String k = lockeyId.toString();
        return s.contains(k) ? BlockPos.fromLong(s.getLong(k)) : null;
    }

    /** Mark a key as gone for good - chest destroyed, or key lost to lava. */
    public static void revoke(ServerWorld world, UUID lockeyId) {
        if (lockeyId == null) return;
        UnbetaWorldState.edit(world, DEAD, s -> s.putBoolean(lockeyId.toString(), true));
    }

    public static boolean isRevoked(ServerWorld world, UUID lockeyId) {
        if (lockeyId == null) return false;
        return UnbetaWorldState.read(world, DEAD).getBoolean(lockeyId.toString());
    }

    /** True if this specific Lockey is the one holding this chest. */
    public static boolean matches(ServerWorld world, BlockPos pos, UUID lockeyId) {
        UUID owner = lockedBy(world, pos);
        return owner != null && owner.equals(lockeyId);
    }
}
