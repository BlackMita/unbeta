package net.unbeta.content.lockey;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

/**
 * Lockey: a key that binds to one chest at a time.
 *
 * Two visual states, driven by the "Bound" NBT flag and surfaced to the model via
 * a float item property so the sprite can swap:
 *   unbound ("keylock") - fresh, lock + key visible
 *   bound   ("keyonly") - currently locking a chest, lock sprite gone
 *
 * When bound we also store the chest position so the key can report it, and a
 * per-Lockey id so a chest can identify which key locked it.
 */
public class LockeyItem extends Item {

    public static final String NBT_BOUND = "LockeyBound";
    public static final String NBT_CHEST_X = "LockeyChestX";
    public static final String NBT_CHEST_Y = "LockeyChestY";
    public static final String NBT_CHEST_Z = "LockeyChestZ";
    public static final String NBT_ID = "LockeyId";

    public LockeyItem(Settings settings) {
        super(settings);
    }

    public static boolean isLockey(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof LockeyItem;
    }

    public static boolean isBound(ItemStack stack) {
        NbtCompound n = stack.getNbt();
        return n != null && n.getBoolean(NBT_BOUND);
    }

    /** Stable per-Lockey id, generated on first use so chests can match against it. */
    public static java.util.UUID getOrCreateId(ItemStack stack) {
        NbtCompound n = stack.getOrCreateNbt();
        if (!n.containsUuid(NBT_ID)) {
            n.putUuid(NBT_ID, java.util.UUID.randomUUID());
        }
        return n.getUuid(NBT_ID);
    }

    public static java.util.UUID getId(ItemStack stack) {
        NbtCompound n = stack.getNbt();
        return (n != null && n.containsUuid(NBT_ID)) ? n.getUuid(NBT_ID) : null;
    }

    public static void bind(ItemStack stack, BlockPos chestPos) {
        NbtCompound n = stack.getOrCreateNbt();
        getOrCreateId(stack);
        n.putBoolean(NBT_BOUND, true);
        n.putInt(NBT_CHEST_X, chestPos.getX());
        n.putInt(NBT_CHEST_Y, chestPos.getY());
        n.putInt(NBT_CHEST_Z, chestPos.getZ());
    }

    /** Unbind, returning the key to keylock form so it can be reused elsewhere. */
    public static void unbind(ItemStack stack) {
        NbtCompound n = stack.getNbt();
        if (n == null) return;
        n.putBoolean(NBT_BOUND, false);
        n.remove(NBT_CHEST_X);
        n.remove(NBT_CHEST_Y);
        n.remove(NBT_CHEST_Z);
    }

    public static BlockPos getBoundChest(ItemStack stack) {
        NbtCompound n = stack.getNbt();
        if (n == null || !n.getBoolean(NBT_BOUND)) return null;
        if (!n.contains(NBT_CHEST_X)) return null;
        return new BlockPos(n.getInt(NBT_CHEST_X), n.getInt(NBT_CHEST_Y), n.getInt(NBT_CHEST_Z));
    }
}
