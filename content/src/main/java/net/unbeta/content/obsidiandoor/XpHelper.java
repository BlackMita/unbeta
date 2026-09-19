package net.unbeta.content.obsidiandoor;

import net.minecraft.entity.player.PlayerEntity;

/**
 * Reliable experience-point math, since PlayerEntity.totalExperience is buggy (it does
 * not decrease when you enchant). We compute the true spendable total from level +
 * progress using the official formulas.
 */
public final class XpHelper {

    private XpHelper() {}

    /** Total XP points needed to reach the START of a given level (wiki formula). */
    public static int pointsForLevel(int level) {
        if (level <= 16) return level * level + 6 * level;
        if (level <= 31) return (int)(2.5 * level * level - 40.5 * level + 360);
        return (int)(4.5 * level * level - 162.5 * level + 2220);
    }

    /** XP points to go from this level to the next. */
    public static int pointsToNext(int level) {
        if (level <= 15) return 2 * level + 7;
        if (level <= 30) return 5 * level - 38;
        return 9 * level - 158;
    }

    /** A player's true current total spendable points. */
    public static int totalPoints(PlayerEntity player) {
        int base = pointsForLevel(player.experienceLevel);
        int intoLevel = Math.round(player.experienceProgress * pointsToNext(player.experienceLevel));
        return base + intoLevel;
    }

    public static boolean canAfford(PlayerEntity player, int cost) {
        return totalPoints(player) >= cost;
    }
}
