package net.unbeta.hg.bot;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Works out how a bot can make an item from what it carries: the whole chain, so 4 logs become
 * planks, then sticks, then a wooden pickaxe. Uses the server's real recipes (so Unbeta's recipe
 * changes apply), and spends real ingredients when each step is crafted.
 */
public final class Crafting {

    private Crafting() {}

    /** One crafting step: a recipe (or, with no recipe, "place the crafting table now"). */
    public record Step(CraftingRecipe recipe, ItemStack output, boolean needsTable) {
        public boolean placeTable() {
            return recipe == null;
        }
    }

    private static final int DEPTH = 4;

    /** The steps to make the item from what the bot carries, or null if it can't. */
    public static List<Step> plan(ServerPlayerEntity bot, Item target, boolean tableNearby) {
        MinecraftServer server = bot.getServer();
        List<ItemStack> inv = snapshot(bot.getInventory());
        List<Step> steps = new ArrayList<>();
        if (!make(server, inv, steps, s -> s.isOf(target), DEPTH)) return null;
        boolean needsTable = steps.stream().anyMatch(Step::needsTable);
        if (!needsTable || tableNearby || has(bot, net.minecraft.item.Items.CRAFTING_TABLE)) return steps;
        // It needs a crafting table first: make one, place it, then the rest.
        inv = snapshot(bot.getInventory());
        steps = new ArrayList<>();
        if (!make(server, inv, steps, s -> s.isOf(net.minecraft.item.Items.CRAFTING_TABLE), DEPTH)) return null;
        take(inv, s -> s.isOf(net.minecraft.item.Items.CRAFTING_TABLE));
        steps.add(new Step(null, ItemStack.EMPTY, false));
        if (!make(server, inv, steps, s -> s.isOf(target), DEPTH)) return null;
        return steps;
    }

    public static boolean has(ServerPlayerEntity bot, Item item) {
        PlayerInventory inv = bot.getInventory();
        for (int i = 0; i < 36; i++) if (inv.getStack(i).isOf(item)) return true;
        return false;
    }

    private static List<CraftingRecipe> recipes(MinecraftServer server) {
        return server.getRecipeManager().listAllOfType(RecipeType.CRAFTING);
    }

    private static boolean needsTable(CraftingRecipe recipe) {
        if (recipe instanceof ShapedRecipe shaped) return shaped.getWidth() > 2 || shaped.getHeight() > 2;
        int n = 0;
        for (Ingredient i : recipe.getIngredients()) if (!i.isEmpty()) n++;
        return n > 4;
    }

    /** Plans making something that passes `want`, using (and updating) the pretend inventory. */
    private static boolean make(MinecraftServer server, List<ItemStack> inv, List<Step> steps, Predicate<ItemStack> want, int depth) {
        if (depth < 0) return false;
        for (CraftingRecipe recipe : recipes(server)) {
            ItemStack out = recipe.getOutput(server.getRegistryManager());
            if (out.isEmpty() || !want.test(out)) continue;
            List<ItemStack> trial = copy(inv);
            List<Step> trialSteps = new ArrayList<>(steps);
            boolean ok = true;
            for (Ingredient ing : recipe.getIngredients()) {
                if (ing.isEmpty()) continue;
                if (take(trial, ing)) continue;
                if (!make(server, trial, trialSteps, ing, depth - 1) || !take(trial, ing)) {
                    ok = false;
                    break;
                }
            }
            if (!ok) continue;
            trial.add(out.copy());
            trialSteps.add(new Step(recipe, out.copy(), needsTable(recipe)));
            inv.clear();
            inv.addAll(trial);
            steps.clear();
            steps.addAll(trialSteps);
            return true;
        }
        return false;
    }

    private static List<ItemStack> snapshot(PlayerInventory inv) {
        List<ItemStack> list = new ArrayList<>();
        for (int i = 0; i < 36; i++) if (!inv.getStack(i).isEmpty()) list.add(inv.getStack(i).copy());
        return list;
    }

    private static List<ItemStack> copy(List<ItemStack> inv) {
        List<ItemStack> list = new ArrayList<>();
        for (ItemStack s : inv) list.add(s.copy());
        return list;
    }

    /** Takes one matching item from a pretend inventory. */
    private static boolean take(List<ItemStack> inv, Predicate<ItemStack> match) {
        for (ItemStack s : inv) {
            if (s.getCount() > 0 && match.test(s)) {
                Item remainder = s.getItem().getRecipeRemainder();
                s.decrement(1);
                if (remainder != null) inv.add(new ItemStack(remainder));
                return true;
            }
        }
        return false;
    }

    /** Can the bot craft this step from what it carries right now? */
    public static boolean canDo(ServerPlayerEntity bot, Step step) {
        List<ItemStack> inv = snapshot(bot.getInventory());
        for (Ingredient ing : step.recipe().getIngredients()) {
            if (!ing.isEmpty() && !take(inv, ing)) return false;
        }
        return true;
    }

    /** Crafts a step for real: spends the ingredients and hands over the result. */
    public static void craft(ServerPlayerEntity bot, Step step) {
        PlayerInventory inv = bot.getInventory();
        for (Ingredient ing : step.recipe().getIngredients()) {
            if (ing.isEmpty()) continue;
            for (int i = 0; i < 36; i++) {
                ItemStack s = inv.getStack(i);
                if (!s.isEmpty() && ing.test(s)) {
                    Item remainder = s.getItem().getRecipeRemainder();
                    s.decrement(1);
                    if (remainder != null) inv.offerOrDrop(new ItemStack(remainder));
                    break;
                }
            }
        }
        inv.offerOrDrop(step.output().copy());
    }
}
