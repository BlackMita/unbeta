package net.unbeta.content.lockey;

import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/**
 * A dead Lockey alone in the grid salvages back into a gold nugget.
 *
 * <p>Only matches a key that is bound AND whose chest is gone - a live key must never be
 * destroyable this way. You get the gold back and lose the iron, so a failed lock costs
 * something without being a total write-off.
 *
 * <p>A special recipe rather than a shapeless JSON because vanilla ingredients in 1.20.1
 * cannot match on NBT, and "a Lockey, but only a dead one" is exactly an NBT condition.
 */
public class LockeySalvageRecipe extends SpecialCraftingRecipe {

    public LockeySalvageRecipe(Identifier id, CraftingRecipeCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(RecipeInputInventory inv, World world) {
        ItemStack found = ItemStack.EMPTY;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty()) continue;
            if (!found.isEmpty()) return false; // more than one item in the grid
            found = s;
        }
        if (found.isEmpty()) return false;
        return LockeyItem.isLockey(found)
                && LockeyItem.isBound(found)
                && LockeyItem.isDead(found);
    }

    @Override
    public ItemStack craft(RecipeInputInventory inv, DynamicRegistryManager registryManager) {
        return new ItemStack(Items.GOLD_NUGGET);
    }

    @Override
    public boolean fits(int width, int height) {
        return width >= 1 && height >= 1;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return LockeyRegistry.SALVAGE_SERIALIZER;
    }
}
