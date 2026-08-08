package net.unbeta.content.torch;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

/**
 * Helpers for the ITEM side of torches. An Unbeta torch item carries two bits of state
 * in NBT: whether it is lit, and its remaining burn-life. The single torch item is used
 * for both lit and unlit; the NBT distinguishes them (keeps it one unstackable item).
 */
public final class TorchItems {

    public static final String NBT_LIT = "UnbetaTorchLit";
    public static final String NBT_TICKS = "UnbetaTorchTicks";

    private TorchItems() {}

    public static boolean isTorchItem(ItemStack stack) {
        return !stack.isEmpty() && stack.isOf(UnbetaTorchRegistry.TORCH_ITEM);
    }

    public static boolean isUnlitTorch(ItemStack stack) {
        return isTorchItem(stack) && !isLit(stack);
    }

    public static boolean isLit(ItemStack stack) {
        return stack.hasNbt() && stack.getNbt() != null && stack.getNbt().getBoolean(NBT_LIT);
    }

    public static long getTicks(ItemStack stack) {
        if (stack.hasNbt() && stack.getNbt() != null && stack.getNbt().contains(NBT_TICKS)) {
            return stack.getNbt().getLong(NBT_TICKS);
        }
        return UnbetaTorchBlockEntity.DEFAULT_BURN_TICKS;
    }

    /** Light a held unlit torch: mark lit, ensure burn-life, and start its item countdown. */
    public static void lightHeldTorch(ItemStack stack) {
        if (!isTorchItem(stack)) return;
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putBoolean(NBT_LIT, true);
        long ticks = nbt.contains(NBT_TICKS) ? nbt.getLong(NBT_TICKS) : UnbetaTorchBlockEntity.DEFAULT_BURN_TICKS;
        if (ticks <= 0) ticks = UnbetaTorchBlockEntity.DEFAULT_BURN_TICKS;
        nbt.putLong(NBT_TICKS, ticks);
        // Start the scheduler item-countdown so a lit torch in inventory burns down.
        UnbetaTorchItemBurnout.stamp(stack, ticks);
    }

    public static void extinguishHeldTorch(ItemStack stack) {
        if (!isTorchItem(stack)) return;
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putBoolean(NBT_LIT, false);
        net.unbeta.core.sched.UnbetaScheduler.clearItemCountdown(stack);
    }
}
