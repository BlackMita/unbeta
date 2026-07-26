package net.unbeta.core.gates;

import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.recipe.RecipeType;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.unbeta.core.UnbetaCore;
import net.unbeta.core.api.ContentKind;
import net.unbeta.core.api.UnbetaApi;
import net.unbeta.core.mixin.RecipeManagerAccessor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Rebuilds a RecipeManager's recipe set with gated-output recipes removed.
 *
 * <p>Reads the private {@code recipes} map via {@link RecipeManagerAccessor}, keeps
 * every recipe whose output item is allowed, and calls {@code setRecipes(...)} which
 * recomputes both the by-type and by-id indexes so nothing is left inconsistent.
 *
 * <p>1.20.1 note: {@code Recipe#getOutput(DynamicRegistryManager)} exists but the
 * no-arg {@code getOutput()} is deprecated-but-present; we use the registry-aware
 * form with the manager's own registries where available and fall back safely.
 */
public final class RecipeGate {

    private RecipeGate() {}

    public static void filter(RecipeManager manager) {
        if (!UnbetaApi.isReady()) return;

        Map<RecipeType<?>, Map<Identifier, Recipe<?>>> byType =
                ((RecipeManagerAccessor) manager).unbeta$getRecipes();

        if (byType == null || byType.isEmpty()) return;

        List<Recipe<?>> kept = new ArrayList<>();
        int removed = 0;

        for (Map<Identifier, Recipe<?>> ofType : byType.values()) {
            for (Recipe<?> recipe : ofType.values()) {
                if (isGatedOutput(recipe)) {
                    removed++;
                } else {
                    kept.add(recipe);
                }
            }
        }

        if (removed > 0) {
            manager.setRecipes(kept);
            UnbetaCore.LOG.info("Recipe gate removed {} recipe(s) with gated output.", removed);
        }
    }

    @SuppressWarnings("deprecation")
    private static boolean isGatedOutput(Recipe<?> recipe) {
        try {
            // 1.20.1: getOutput(DynamicRegistryManager). Pass EMPTY - output item identity
            // does not depend on dynamic registries for the vanilla recipes we gate.
            ItemStack out = recipe.getOutput(net.minecraft.registry.DynamicRegistryManager.EMPTY);
            if (out == null || out.isEmpty()) return false;
            Identifier id = Registries.ITEM.getId(out.getItem());
            return UnbetaApi.rules().isRemoved(ContentKind.ITEM, id);
        } catch (Throwable t) {
            return false; // never remove a recipe we could not evaluate
        }
    }
}
