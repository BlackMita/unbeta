package net.unbeta.content.mob;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Reusable "is this mob (or its target) standing in light?" check.
 *
 * <p>Intended to back several Phase 2 mob behaviours: endermen teleport away from lit
 * areas, and (later) zombies may behave differently in light. Centralised here so the
 * threshold and the definition of "in light" stay consistent across mobs.
 *
 * <p>Threshold is 9: at light level 9 you are genuinely within a torch's useful radius
 * (a torch emits 14 and falls to 9 about five blocks out), so "near an active light
 * source" reliably counts as protected, while ambient gloom does not.
 */
public final class MobLightAwareness {

    public static final int LIGHT_THRESHOLD = 10;

    private MobLightAwareness() {}

    /**
     * Effective light at a position RIGHT NOW, accounting for time of day. This is the
     * time-aware overload (getLightLevel(pos) internally uses getAmbientDarkness()), so at
     * midnight an exposed block correctly reads dark - unlike getLightLevel(SKY, pos),
     * which stays 15 at night and was the original bug.
     */
    public static int lightAt(World world, BlockPos pos) {
        return world.getLightLevel(pos);
    }

    public static boolean inLight(World world, BlockPos pos) {
        return lightAt(world, pos) >= LIGHT_THRESHOLD;
    }

    /**
     * True if EITHER the mob's own position OR its target's position is lit at/above the
     * threshold. Used by endermen: being in light protects the player, and a lit enderman
     * also wants to flee.
     */
    public static boolean mobOrTargetInLight(MobEntity mob) {
        World world = mob.getWorld();
        if (inLight(world, mob.getBlockPos())) return true;
        LivingEntity target = mob.getTarget();
        return target != null && inLight(world, target.getBlockPos());
    }
}
