package net.unbeta.content.enderman;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Endermen can't be hurt, but striking one knocks a little experience out of it - a smaller
 * echo of a Guardian Post. A critical hit, an arrow or any other projectile from a player
 * knocks out 2 or 3 points (evenly, 2.5 on average); an ordinary hit, 1 point. At most once
 * every half second per Enderman.
 */
public final class EndermanStrikeXp {

    /** A fifth of a fifth. */
    public static final int PLAIN_POINTS = 1;
    private static final int COOLDOWN = 10;

    private static final Map<EndermanEntity, Long> LAST = new WeakHashMap<>();

    private EndermanStrikeXp() {}

    public static void struck(EndermanEntity man, DamageSource source) {
        if (!(man.getWorld() instanceof ServerWorld world)) return;
        if (!(source.getAttacker() instanceof PlayerEntity player) || player.isSpectator()) return;
        long now = world.getTime();
        Long last = LAST.get(man);
        if (last != null && now - last < COOLDOWN) return;
        LAST.put(man, now);
        Entity direct = source.getSource();
        boolean projectile = direct != null && direct != player;
        int points = projectile || critical(player) ? 2 + world.random.nextInt(2) : PLAIN_POINTS;
        ExperienceOrbEntity.spawn(world, man.getPos().add(0, man.getHeight() * 0.6, 0), points);
    }

    /** The same airborne conditions vanilla checks for a critical hit. */
    private static boolean critical(PlayerEntity p) {
        return p.fallDistance > 0.0F && !p.isOnGround() && !p.isClimbing() && !p.isTouchingWater()
                && !p.hasStatusEffect(StatusEffects.BLINDNESS) && !p.hasVehicle() && !p.isSprinting();
    }
}
