package net.unbeta.content.corruption;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.MooshroomEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.unbeta.content.zombie.SulliedChunkState;

/**
 * The rules of zombie corruption, in one place.
 *
 * <ul>
 *   <li>Carriers: zombies (every variant but the Unmason) and corrupted animals.</li>
 *   <li>Prey: players, and healthy cows, pigs, sheep and chickens. Never wolves.</li>
 *   <li>A carrier that damages healthy livestock leaves a hidden, saved "bitten" tag.</li>
 *   <li>What a death leaves in the chunk's memory: a zombie is remembered as a zombie, a
 *       corrupted animal as itself, a bitten animal as its corrupted form. Deaths while
 *       burning, or to a player's gold sword or gold axe, are clean and leave nothing.</li>
 * </ul>
 */
public final class CorruptionMemory {

    /** Hidden, saved on the entity (visible only via /data). */
    public static final String BITTEN_TAG = "unbeta_bitten";

    private CorruptionMemory() {}

    /** The corrupted counterpart of a livestock animal, or null if it has none. */
    public static EntityType<?> corruptedFormOf(Entity e) {
        if (e instanceof CorruptedAnimal) return null;   // already corrupted
        if (e instanceof MooshroomEntity) return null;   // a cow subclass, but not livestock here
        if (e instanceof CowEntity) return CorruptionRegistry.ZOMBIE_COW;
        if (e instanceof PigEntity) return CorruptionRegistry.ZOMBIE_PIG;
        if (e instanceof SheepEntity) return CorruptionRegistry.ZOMBIE_SHEEP;
        if (e instanceof ChickenEntity) return CorruptionRegistry.ZOMBIE_CHICKEN;
        return null;
    }

    public static boolean isHealthyLivestock(Entity e) {
        return corruptedFormOf(e) != null;
    }

    public static boolean isCarrier(Entity e) {
        return (e instanceof ZombieEntity && !(e instanceof net.unbeta.content.unmason.UnmasonEntity))
                || e instanceof CorruptedAnimal;
    }

    /** What zombies and corrupted animals hunt. Creative/spectator players are filtered by vanilla. */
    public static boolean isPrey(LivingEntity e) {
        return e instanceof PlayerEntity || isHealthyLivestock(e);
    }

    /** Burnt up, or killed with a player's gold sword or gold axe. */
    public static boolean isCleanDeath(LivingEntity victim, DamageSource source) {
        if (victim.isOnFire()) return true;
        return source.getAttacker() instanceof PlayerEntity player
                && (player.getMainHandStack().isOf(Items.GOLDEN_SWORD)
                    || player.getMainHandStack().isOf(Items.GOLDEN_AXE));
    }

    /** The entity type id a chunk should remember for this death, or null for none. */
    public static String rememberedAs(LivingEntity e) {
        if (e instanceof ZombieEntity) {
            return e instanceof net.unbeta.content.unmason.UnmasonEntity ? null : SulliedChunkState.ZOMBIE;
        }
        if (e instanceof CorruptedAnimal) {
            return Registries.ENTITY_TYPE.getId(e.getType()).toString();
        }
        if (e.getCommandTags().contains(BITTEN_TAG)) {
            EntityType<?> form = corruptedFormOf(e);
            return form == null ? null : Registries.ENTITY_TYPE.getId(form).toString();
        }
        return null;
    }
}
