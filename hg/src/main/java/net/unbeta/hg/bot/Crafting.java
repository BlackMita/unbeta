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

    private static final int DEPTH = 3;
    /** Most recipe trials one plan may make, so no plan can ever stall the server. */
    private static final int BUDGET = 400;

    /** The steps to make the item from what the bot carries, or null if it can't. */
    public static List<Step> plan(ServerPlayerEntity bot, Item target, boolean tableNearby) {
        return plan(bot, s -> s.isOf(target), tableNearby, index(bot.getServer()).makes(target));
    }

    /** The steps to make anything that passes the test (any planks, say), or null if it can't. */
    public static List<Step> plan(ServerPlayerEntity bot, Predicate<ItemStack> want, boolean tableNearby) {
        return plan(bot, want, tableNearby, index(bot.getServer()).all);
    }

    private static List<Step> plan(ServerPlayerEntity bot, Predicate<ItemStack> want, boolean tableNearby, List<CraftingRecipe> candidates) {
        MinecraftServer server = bot.getServer();
        Index idx = index(server);
        List<ItemStack> inv = snapshot(bot.getInventory());
        List<Step> steps = new ArrayList<>();
        int[] budget = {BUDGET};
        if (!make(server, idx, inv, steps, want, candidates, DEPTH, budget, new java.util.HashSet<>())) return null;
        boolean needsTable = steps.stream().anyMatch(Step::needsTable);
        if (!needsTable || tableNearby || has(bot, net.minecraft.item.Items.CRAFTING_TABLE)) return steps;
        // It needs a crafting table first: make one, place it, then the rest.
        inv = snapshot(bot.getInventory());
        steps = new ArrayList<>();
        budget[0] = BUDGET;
        if (!make(server, idx, inv, steps, s -> s.isOf(net.minecraft.item.Items.CRAFTING_TABLE),
                idx.makes(net.minecraft.item.Items.CRAFTING_TABLE), DEPTH, budget, new java.util.HashSet<>())) return null;
        take(inv, s -> s.isOf(net.minecraft.item.Items.CRAFTING_TABLE));
        steps.add(new Step(null, ItemStack.EMPTY, false));
        budget[0] = BUDGET;
        if (!make(server, idx, inv, steps, want, candidates, DEPTH, budget, new java.util.HashSet<>())) return null;
        return steps;
    }

    public static boolean has(ServerPlayerEntity bot, Item item) {
        PlayerInventory inv = bot.getInventory();
        for (int i = 0; i < 36; i++) if (inv.getStack(i).isOf(item)) return true;
        return false;
    }

    /** Every crafting recipe, once, and which recipes make each item (rebuilt if recipes reload). */
    private static final class Index {
        final Object manager;
        final List<CraftingRecipe> all;
        final java.util.Map<Item, List<CraftingRecipe>> byOutput = new java.util.HashMap<>();

        Index(MinecraftServer server) {
            manager = server.getRecipeManager();
            all = new ArrayList<>(server.getRecipeManager().listAllOfType(RecipeType.CRAFTING));
            for (CraftingRecipe r : all) {
                ItemStack out = r.getOutput(server.getRegistryManager());
                if (!out.isEmpty()) byOutput.computeIfAbsent(out.getItem(), k -> new ArrayList<>()).add(r);
            }
        }

        List<CraftingRecipe> makes(Item item) {
            return byOutput.getOrDefault(item, List.of());
        }

        /** Recipes that make anything an ingredient accepts. */
        List<CraftingRecipe> makes(Ingredient ing) {
            List<CraftingRecipe> out = new ArrayList<>();
            java.util.Set<Item> seen = new java.util.HashSet<>();
            for (ItemStack s : ing.getMatchingStacks()) if (seen.add(s.getItem())) out.addAll(makes(s.getItem()));
            return out;
        }
    }

    private static Index index;

    private static Index index(MinecraftServer server) {
        if (index == null || index.manager != server.getRecipeManager()) index = new Index(server);
        return index;
    }

    private static boolean needsTable(CraftingRecipe recipe) {
        if (recipe instanceof ShapedRecipe shaped) return shaped.getWidth() > 2 || shaped.getHeight() > 2;
        int n = 0;
        for (Ingredient i : recipe.getIngredients()) if (!i.isEmpty()) n++;
        return n > 4;
    }

    /**
     * Plans making something that passes `want` from the candidate recipes, using (and updating)
     * the pretend inventory. Never tries to make an item while already making it (Unbeta's dyed
     * wood loops planks back into planks), and gives up when the budget runs out.
     */
    private static boolean make(MinecraftServer server, Index idx, List<ItemStack> inv, List<Step> steps, Predicate<ItemStack> want,
                                List<CraftingRecipe> candidates, int depth, int[] budget, java.util.Set<Item> making) {
        if (depth < 0) return false;
        for (CraftingRecipe recipe : candidates) {
            ItemStack out = recipe.getOutput(server.getRegistryManager());
            if (out.isEmpty() || !want.test(out) || making.contains(out.getItem())) continue;
            if (--budget[0] < 0) return false;
            List<ItemStack> trial = copy(inv);
            List<Step> trialSteps = new ArrayList<>(steps);
            boolean ok = true;
            making.add(out.getItem());
            for (Ingredient ing : recipe.getIngredients()) {
                if (ing.isEmpty()) continue;
                if (take(trial, ing)) continue;
                if (!make(server, idx, trial, trialSteps, ing, idx.makes(ing), depth - 1, budget, making) || !take(trial, ing)) {
                    ok = false;
                    break;
                }
            }
            making.remove(out.getItem());
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
