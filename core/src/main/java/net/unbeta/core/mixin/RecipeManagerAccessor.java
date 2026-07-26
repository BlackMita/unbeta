package net.unbeta.core.mixin;

import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.recipe.RecipeType;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * Read-only accessor for RecipeManager's private recipe map.
 *
 * <p>1.20.1 shape: {@code Map<RecipeType<?>, Map<Identifier, Recipe<?>>>}.
 * (1.20.2 renamed the value type to RecipeEntry - do not copy this to a newer MC
 * version without checking.)
 */
@Mixin(RecipeManager.class)
public interface RecipeManagerAccessor {
    @Accessor("recipes")
    Map<RecipeType<?>, Map<Identifier, Recipe<?>>> unbeta$getRecipes();
}
