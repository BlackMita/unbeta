package net.unbeta.core.state;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

/**
 * Per-world persistent storage for Unbeta features.
 *
 * <p>One PersistentState per dimension, saved with the world, holding a single NbtCompound
 * that features carve up by namespace. A feature asks for its own sub-compound by name and
 * reads/writes freely inside it; nothing can collide with anything else.
 *
 * <p>Callers MUST call {@link #markDirty()} (or use {@link #edit}) after changing data,
 * or the change will not be written to disk.
 *
 * <p>Intended users: Lockey (chest -> key bindings), Scarecrow (placed effigy state),
 * and anything else that needs to remember something about the world across restarts.
 */
public class UnbetaWorldState extends PersistentState {

    /** Save file name: data/unbeta_world.dat inside the dimension folder. */
    private static final String FILE_ID = "unbeta_world";

    private final NbtCompound root;

    public UnbetaWorldState() {
        this.root = new NbtCompound();
    }

    private UnbetaWorldState(NbtCompound root) {
        this.root = root;
    }

    private static UnbetaWorldState fromNbt(NbtCompound nbt) {
        return new UnbetaWorldState(nbt.getCompound("Root"));
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.put("Root", root);
        return nbt;
    }

    /** Get (or create) the state for this world. Server-side only. */
    public static UnbetaWorldState get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(
                UnbetaWorldState::fromNbt,
                UnbetaWorldState::new,
                FILE_ID);
    }

    /**
     * The sub-compound owned by one feature. Created on first access.
     * Mutating the returned compound mutates the stored data directly.
     */
    public NbtCompound section(String name) {
        if (!root.contains(name)) {
            root.put(name, new NbtCompound());
        }
        return root.getCompound(name);
    }

    /**
     * Edit a section and mark dirty automatically - the safe way to write, since
     * forgetting markDirty() silently loses data on restart.
     */
    public void edit(String name, java.util.function.Consumer<NbtCompound> mutator) {
        mutator.accept(section(name));
        markDirty();
    }

    /** Convenience: edit a section of the given world in one call. */
    public static void edit(ServerWorld world, String name,
                            java.util.function.Consumer<NbtCompound> mutator) {
        get(world).edit(name, mutator);
    }

    /** Convenience: read a section without marking dirty. */
    public static NbtCompound read(ServerWorld world, String name) {
        return get(world).section(name);
    }
}
