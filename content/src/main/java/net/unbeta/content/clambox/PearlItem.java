package net.unbeta.content.clambox;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

/**
 * A pearl wraps exactly one item inside it. Thrown (phase 4) it breaks and releases
 * that item. If the wrapped item is itself a pearl, you peel the onion until a
 * non-pearl falls out - the nesting a clambox produces when you pearl a pearl.
 */
public class PearlItem extends Item {

    public static final String NBT_CONTENTS = "PearlContents"; // a serialized ItemStack
    public static final String NBT_DEPTH = "PearlDepth";       // nesting depth, 1-based

    /** Hard cap on how deep pearls may nest, to bound NBT size and throw cost. */
    public static final int MAX_DEPTH = 8;

    public PearlItem(Settings settings) {
        super(settings);
    }

    public static boolean isPearl(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof PearlItem;
    }

    /** Wrap a stack into a new pearl. The wrapped stack is copied at count 1. */
    public static ItemStack wrap(ItemStack contents) {
        ItemStack pearl = new ItemStack(ClamboxRegistry.PEARL_ITEM);
        NbtCompound stored = new NbtCompound();
        ItemStack one = contents.copy();
        one.setCount(1);
        one.writeNbt(stored);

        NbtCompound nbt = pearl.getOrCreateNbt();
        nbt.put(NBT_CONTENTS, stored);
        nbt.putInt(NBT_DEPTH, depthOf(contents) + 1);
        return pearl;
    }

    /** The item stored inside this pearl, or EMPTY if somehow none. */
    public static ItemStack contentsOf(ItemStack pearl) {
        NbtCompound n = pearl.getNbt();
        if (n == null || !n.contains(NBT_CONTENTS)) return ItemStack.EMPTY;
        return ItemStack.fromNbt(n.getCompound(NBT_CONTENTS));
    }

    /** Nesting depth of a stack: 0 for a normal item, N for an N-deep pearl. */
    public static int depthOf(ItemStack stack) {
        if (!isPearl(stack)) return 0;
        NbtCompound n = stack.getNbt();
        return (n != null && n.contains(NBT_DEPTH)) ? n.getInt(NBT_DEPTH) : 1;
    }

    /** Can this stack be pearled, or is it already at the depth cap? */
    public static boolean canPearl(ItemStack stack) {
        return !stack.isEmpty() && depthOf(stack) < MAX_DEPTH;
    }
}
