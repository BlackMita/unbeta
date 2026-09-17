package net.unbeta.content.clambox;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.math.random.Random;

import java.util.List;

/**
 * What a naturally-found clambox has pre-loaded in its output slot.
 *
 * <p>Four rarity tiers. Within each tier, a 1-in-8 roll wraps the item in a pearl instead
 * of giving it raw - so pearls appear at every rarity, most often holding common junk.
 * That's deliberate: it teaches the pearl mechanic through low-stakes finds before a
 * player ever pearls something valuable themselves.
 *
 * <p>Tools always spawn damaged - a found tool is salvage, not fresh craft.
 */
public final class ClamboxLoot {

    private ClamboxLoot() {}

    // per-tier chance (1 in N) that the rolled item is wrapped in a pearl
    private static final int WRAP_CHANCE = 8;

    private static final List<Item> COMMON = List.of(
            Items.BONE, Items.STRING, Items.STICK, Items.ROTTEN_FLESH,
            Items.INK_SAC, Items.CLAY_BALL, Items.DIRT, Items.SAND,
            Items.GRAVEL, Items.FLINT, Items.IRON_NUGGET, Items.GOLD_NUGGET);

    private static final List<Item> UNCOMMON = List.of(
            Items.REDSTONE, Items.SLIME_BALL, Items.LAPIS_LAZULI,
            Items.MOSSY_COBBLESTONE);

    private static final List<Item> UNCOMMON_TOOLS = List.of(
            Items.WOODEN_PICKAXE, Items.WOODEN_AXE,
            Items.WOODEN_SHOVEL, Items.WOODEN_SWORD);

    private static final List<Item> RARE = List.of(
            Items.IRON_INGOT, Items.GOLD_INGOT);

    private static final List<Item> ULTRA_TOOLS = List.of(
            Items.IRON_PICKAXE, Items.IRON_SWORD,
            Items.GOLDEN_PICKAXE, Items.GOLDEN_SWORD);

    /** Roll a single loot result for a found clambox's output slot.
     * Always returns a pearl - the item is always at least 1 layer deep. */
    public static ItemStack roll(Random random) {
        int r = random.nextInt(100);
        // 55 common / 30 uncommon / 12 rare / 3 ultra-rare
        if (r < 55) {
            return maybeWrap(new ItemStack(pick(COMMON, random)), random, 1);
        } else if (r < 85) {
            // uncommon: half plain uncommon, half a damaged wood tool
            ItemStack base = random.nextBoolean()
                    ? new ItemStack(pick(UNCOMMON, random))
                    : damaged(pick(UNCOMMON_TOOLS, random), random);
            return maybeWrap(base, random, 1);
        } else if (r < 97) {
            return maybeWrap(new ItemStack(pick(RARE, random)), random, 1);
        } else {
            // ultra-rare: damaged metal tool, or a deeper nested pearl
            if (random.nextInt(3) == 0) {
                // a 2-3 deep pearl of a good item
                ItemStack inner = new ItemStack(pick(RARE, random));
                ItemStack pearl = PearlItem.wrap(inner);
                if (random.nextBoolean()) pearl = PearlItem.wrap(pearl); // one deeper
                return pearl;
            }
            return maybeWrap(damaged(pick(ULTRA_TOOLS, random), random), random, 1);
        }
    }

    /** Always wraps in a pearl. With a 1-in-WRAP_CHANCE roll, wraps an extra layer. */
    private static ItemStack maybeWrap(ItemStack stack, Random random, int depth) {
        // Always at least 1-deep pearl
        ItemStack pearl = PearlItem.isPearl(stack) ? stack : PearlItem.wrap(stack);
        // Chance of an extra layer
        if (random.nextInt(WRAP_CHANCE) == 0 && PearlItem.canPearl(pearl)) {
            pearl = PearlItem.wrap(pearl);
        }
        return pearl;
    }

    private static ItemStack damaged(Item tool, Random random) {
        ItemStack s = new ItemStack(tool);
        int max = s.getMaxDamage();
        if (max > 0) {
            // between 30% and 85% worn
            int dmg = max * 3 / 10 + random.nextInt(max * 55 / 100);
            s.setDamage(dmg);
        }
        return s;
    }

    private static Item pick(List<Item> pool, Random random) {
        return pool.get(random.nextInt(pool.size()));
    }
}
