package net.unbeta.content.torch;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

/**
 * ITEM-side torch state. ONE RULE:
 *
 * <ul>
 *   <li><b>LIT</b>  -> {@code NBT_BURNOUT_AT} (absolute world time it goes out) is the
 *       single source of truth. It is NOT recomputed while the torch moves around, so a
 *       lit torch burns correctly whether held, stored, dropped, or placed.</li>
 *   <li><b>UNLIT</b> -> {@code NBT_TICKS} (frozen remaining ticks) is the truth.</li>
 * </ul>
 *
 * Conversion happens ONLY at a lit/unlit transition. Converting anywhere else was what
 * silently refilled torches on pickup.
 */
public final class TorchItems {

    public static final String NBT_LIT = "UnbetaTorchLit";
    public static final String NBT_TICKS = "UnbetaTorchTicks";        // remaining, valid when UNLIT
    public static final String NBT_BURNOUT_AT = "UnbetaTorchBurnoutAt"; // deadline, valid when LIT
    public static final String NBT_FULL = "UnbetaTorchFull";           // fixed bar denominator

    private TorchItems() {}

    public static boolean isTorchItem(ItemStack stack) {
        return !stack.isEmpty() && stack.isOf(UnbetaTorchRegistry.TORCH_ITEM);
    }

    public static boolean isLit(ItemStack stack) {
        return stack.hasNbt() && stack.getNbt() != null && stack.getNbt().getBoolean(NBT_LIT);
    }

    public static boolean isUnlitTorch(ItemStack stack) {
        return isTorchItem(stack) && !isLit(stack);
    }

    public static long getFull(ItemStack stack) {
        NbtCompound n = stack.getNbt();
        return (n != null && n.contains(NBT_FULL)) ? n.getLong(NBT_FULL)
                : UnbetaTorchBlockEntity.DEFAULT_BURN_TICKS;
    }

    /** Remaining ticks. Uses burnoutAt when lit, frozen ticks when unlit. */
    public static long getRemaining(ItemStack stack, long now) {
        NbtCompound n = stack.getNbt();
        if (n == null) return UnbetaTorchBlockEntity.DEFAULT_BURN_TICKS;
        if (isLit(stack) && n.contains(NBT_BURNOUT_AT)) {
            return Math.max(0, n.getLong(NBT_BURNOUT_AT) - now);
        }
        return n.contains(NBT_TICKS) ? n.getLong(NBT_TICKS)
                : UnbetaTorchBlockEntity.DEFAULT_BURN_TICKS;
    }

    /** Absolute deadline, or -1 if not lit. */
    public static long getBurnoutAt(ItemStack stack) {
        NbtCompound n = stack.getNbt();
        return (n != null && n.contains(NBT_BURNOUT_AT)) ? n.getLong(NBT_BURNOUT_AT) : -1L;
    }

    /** LIGHT: convert frozen remaining -> absolute deadline. The only conversion in. */
    public static void lightHeldTorch(ItemStack stack, long now) {
        if (!isTorchItem(stack)) return;
        NbtCompound nbt = stack.getOrCreateNbt();
        long remaining = nbt.contains(NBT_TICKS) ? nbt.getLong(NBT_TICKS)
                : UnbetaTorchBlockEntity.DEFAULT_BURN_TICKS;
        if (remaining <= 0) remaining = UnbetaTorchBlockEntity.DEFAULT_BURN_TICKS;
        if (!nbt.contains(NBT_FULL)) nbt.putLong(NBT_FULL, UnbetaTorchBlockEntity.DEFAULT_BURN_TICKS);
        nbt.putBoolean(NBT_LIT, true);
        nbt.putLong(NBT_BURNOUT_AT, now + remaining);
        nbt.remove(NBT_TICKS); // burnoutAt is authoritative while lit
    }

    /** EXTINGUISH: convert absolute deadline -> frozen remaining. The only conversion out. */
    public static void extinguishHeldTorch(ItemStack stack, long now) {
        if (!isTorchItem(stack)) return;
        NbtCompound nbt = stack.getOrCreateNbt();
        long remaining = nbt.contains(NBT_BURNOUT_AT)
                ? Math.max(0, nbt.getLong(NBT_BURNOUT_AT) - now)
                : UnbetaTorchBlockEntity.DEFAULT_BURN_TICKS;
        if (remaining <= 0) remaining = UnbetaTorchBlockEntity.DEFAULT_BURN_TICKS; // spent -> relightable
        nbt.putBoolean(NBT_LIT, false);
        nbt.putLong(NBT_TICKS, remaining);
        nbt.remove(NBT_BURNOUT_AT);
    }

    /** Stamp a lit torch item directly from a known deadline (used when mining a lit block). */
    public static void stampLitFromBurnout(ItemStack stack, long burnoutAt, long full) {
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putBoolean(NBT_LIT, true);
        nbt.putLong(NBT_BURNOUT_AT, burnoutAt);
        nbt.putLong(NBT_FULL, full);
        nbt.remove(NBT_TICKS);
    }
}
