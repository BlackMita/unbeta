package net.unbeta.content.jackolantern;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

/** Item-side state for Unbeta Jack o'Lanterns. Mirrors TorchItems exactly. */
public final class JackOLanternItems {

    public static final String NBT_BURNOUT_AT = "UnbetaJolBurnoutAt";
    public static final String NBT_FULL       = "UnbetaJolFull";

    private JackOLanternItems() {}

    public static boolean isUnlit(ItemStack stack) {
        return !stack.isEmpty() && stack.isOf(JackOLanternRegistry.UNLIT_ITEM);
    }

    public static boolean isLit(ItemStack stack) {
        return !stack.isEmpty() && stack.isOf(JackOLanternRegistry.LIT_ITEM);
    }

    public static long getFull(ItemStack stack) {
        NbtCompound n = stack.getNbt();
        return (n != null && n.contains(NBT_FULL) && n.getLong(NBT_FULL) > 0)
                ? n.getLong(NBT_FULL) : JackOLanternLogic.FULL_BURN_TICKS;
    }

    public static long getBurnoutAt(ItemStack stack) {
        NbtCompound n = stack.getNbt();
        return (n != null && n.contains(NBT_BURNOUT_AT)) ? n.getLong(NBT_BURNOUT_AT) : -1L;
    }

    public static ItemStack createLit(ItemStack from, long now) {
        long full = getFull(from);
        ItemStack lit = new ItemStack(JackOLanternRegistry.LIT_ITEM);
        NbtCompound nbt = lit.getOrCreateNbt();
        nbt.putLong(NBT_FULL, full);
        nbt.putLong(NBT_BURNOUT_AT, now + full);
        return lit;
    }

    public static ItemStack createUnlit() {
        return new ItemStack(JackOLanternRegistry.UNLIT_ITEM);
    }

    public static ItemStack createLitFromBlock(long burnoutAt, long full) {
        ItemStack lit = new ItemStack(JackOLanternRegistry.LIT_ITEM);
        NbtCompound nbt = lit.getOrCreateNbt();
        nbt.putLong(NBT_FULL, full > 0 ? full : JackOLanternLogic.FULL_BURN_TICKS);
        nbt.putLong(NBT_BURNOUT_AT, burnoutAt);
        return lit;
    }
}
