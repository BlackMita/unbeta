package net.unbeta.content.torch;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

/**
 * TWO items now: TORCH_ITEM (unlit) and LIT_TORCH_ITEM (lit).
 * Lit/unlit state is determined by WHICH item it is, not by NBT.
 * NBT stores only burnoutAt (absolute deadline) and full (bar denominator).
 *
 * ONE RULE: lighting always gives a FULL burn. Extinguishing discards remaining.
 */
public final class TorchItems {

    public static final String NBT_BURNOUT_AT = "UnbetaTorchBurnoutAt";
    public static final String NBT_FULL       = "UnbetaTorchFull";

    private TorchItems() {}

    public static boolean isUnlitTorch(ItemStack stack) {
        return !stack.isEmpty() && stack.isOf(UnbetaTorchRegistry.TORCH_ITEM);
    }

    public static boolean isLitTorch(ItemStack stack) {
        return !stack.isEmpty() && stack.isOf(UnbetaTorchRegistry.LIT_TORCH_ITEM);
    }

    public static boolean isTorchItem(ItemStack stack) {
        return isUnlitTorch(stack) || isLitTorch(stack);
    }

    public static boolean isLit(ItemStack stack) {
        return isLitTorch(stack);
    }

    public static long getFull(ItemStack stack) {
        NbtCompound n = stack.getNbt();
        return (n != null && n.contains(NBT_FULL) && n.getLong(NBT_FULL) > 0)
                ? n.getLong(NBT_FULL) : UnbetaTorchBlockEntity.DEFAULT_BURN_TICKS;
    }

    public static long getBurnoutAt(ItemStack stack) {
        NbtCompound n = stack.getNbt();
        return (n != null && n.contains(NBT_BURNOUT_AT)) ? n.getLong(NBT_BURNOUT_AT) : -1L;
    }

    /** Create a new LIT torch from an unlit one - always full burn. */
    public static ItemStack createLit(ItemStack from, long now) {
        long full = getFull(from);
        ItemStack lit = new ItemStack(UnbetaTorchRegistry.LIT_TORCH_ITEM);
        NbtCompound nbt = lit.getOrCreateNbt();
        nbt.putLong(NBT_FULL, full);
        nbt.putLong(NBT_BURNOUT_AT, now + full);
        return lit;
    }

    /** Create a new UNLIT torch (burned out or extinguished). */
    public static ItemStack createUnlit() {
        return new ItemStack(UnbetaTorchRegistry.TORCH_ITEM);
    }

    /** Create a lit torch carrying a known burnout deadline (for mining a placed lit torch). */
    public static ItemStack createLitFromBlock(long burnoutAt, long full) {
        ItemStack lit = new ItemStack(UnbetaTorchRegistry.LIT_TORCH_ITEM);
        NbtCompound nbt = lit.getOrCreateNbt();
        nbt.putLong(NBT_FULL, full > 0 ? full : UnbetaTorchBlockEntity.DEFAULT_BURN_TICKS);
        nbt.putLong(NBT_BURNOUT_AT, burnoutAt);
        return lit;
    }

    /**
     * Light exactly ONE unlit torch from the player's held stack.
     * If stack > 1: decrement by 1, add lit torch to inventory (or drop if full).
     * If stack == 1: replace held item with lit torch.
     */
    public static void lightOneFromStack(net.minecraft.entity.player.PlayerEntity player,
                                         net.minecraft.util.Hand hand, long now) {
        net.minecraft.item.ItemStack held = player.getStackInHand(hand);
        net.minecraft.item.ItemStack lit = createLit(held, now);
        if (held.getCount() > 1) {
            held.decrement(1);
            player.setStackInHand(hand, held);
            if (!player.getInventory().insertStack(lit)) {
                player.dropItem(lit, false);
            }
        } else {
            player.setStackInHand(hand, lit);
        }
    }

}