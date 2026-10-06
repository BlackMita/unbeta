package net.unbeta.content.mimic;

import net.minecraft.item.ItemStack;

import java.util.List;

/** Implemented on Better Mimic's MimicEntity by MimicEntityMixin. */
public interface MimicAccess {
    /** The mimic's own contents - what it has swallowed, and drops when it dies. */
    List<ItemStack> unbeta_contents();
}
