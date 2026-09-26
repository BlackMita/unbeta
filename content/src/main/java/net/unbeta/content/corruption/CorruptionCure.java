package net.unbeta.content.corruption;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

/**
 * The golden apple cure.
 *
 * <ul>
 *   <li>Corrupted animal: instantly replaced by the healthy animal (position, facing,
 *       custom name, sheep colour and shearing kept). It's removed, not killed, so its
 *       chunk remembers nothing.</li>
 *   <li>Bitten livestock (infected, not yet corrupted): eats the apple, bite cleared.</li>
 *   <li>Healthy livestock: refuses the apple, which is kept.</li>
 *   <li>Zombies: nothing yet (the apple isn't used) - a cured zombie becomes a human,
 *       which doesn't exist yet.</li>
 * </ul>
 *
 * <p>Bites are tracked only on the server, so the client can't know whether an animal will
 * accept. It always claims the click for livestock + golden apple; otherwise it would fall
 * through to the player eating the apple themselves while the server fed it to the animal.
 */
public final class CorruptionCure {

    private CorruptionCure() {}

    /** Shared by corrupted animals (their interactMob) and livestock (GoldenAppleFeedMixin). */
    public static ActionResult tryFeed(MobEntity animal, PlayerEntity player, Hand hand) {
        ItemStack held = player.getStackInHand(hand);
        if (!held.isOf(Items.GOLDEN_APPLE)) return ActionResult.PASS;

        boolean corrupted = animal instanceof CorruptedAnimal;
        if (!corrupted && !CorruptionMemory.isHealthyLivestock(animal)) return ActionResult.PASS;
        if (animal.isRemoved()) return ActionResult.PASS;

        // Client: can't see bites. Claim the click; the server decides what actually happens.
        if (!(animal.getWorld() instanceof ServerWorld sw)) return ActionResult.SUCCESS;

        if (corrupted) {
            if (!player.getAbilities().creativeMode) held.decrement(1);
            cure(sw, animal);
            return ActionResult.CONSUME;
        }

        // Healthy livestock won't take it; the apple is kept.
        if (!animal.getCommandTags().contains(CorruptionMemory.BITTEN_TAG)) return ActionResult.PASS;

        if (!player.getAbilities().creativeMode) held.decrement(1);
        animal.getCommandTags().remove(CorruptionMemory.BITTEN_TAG);
        sw.playSound(null, animal.getBlockPos(), SoundEvents.ENTITY_GENERIC_EAT,
                SoundCategory.NEUTRAL, 1.0F, 1.0F);
        sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
                animal.getX(), animal.getBodyY(0.6), animal.getZ(), 6, 0.3, 0.3, 0.3, 0.0);
        return ActionResult.CONSUME;
    }

    private static EntityType<?> healthyFormOf(Entity e) {
        if (e instanceof ZombieCowEntity) return EntityType.COW;
        if (e instanceof ZombiePigEntity) return EntityType.PIG;
        if (e instanceof ZombieSheepEntity) return EntityType.SHEEP;
        if (e instanceof ZombieChickenEntity) return EntityType.CHICKEN;
        return null;
    }

    private static void cure(ServerWorld sw, MobEntity corrupted) {
        EntityType<?> type = healthyFormOf(corrupted);
        if (type == null) return;
        Entity created = type.create(sw);
        if (!(created instanceof MobEntity healthy)) return;

        healthy.refreshPositionAndAngles(corrupted.getX(), corrupted.getY(), corrupted.getZ(),
                corrupted.getYaw(), corrupted.getPitch());
        healthy.setHeadYaw(corrupted.getHeadYaw());
        healthy.setBodyYaw(corrupted.getBodyYaw());
        if (corrupted.hasCustomName()) {
            healthy.setCustomName(corrupted.getCustomName());
            healthy.setCustomNameVisible(corrupted.isCustomNameVisible());
        }
        if (corrupted.isPersistent()) healthy.setPersistent();
        if (corrupted instanceof SheepEntity from && healthy instanceof SheepEntity to) {
            to.setColor(from.getColor());
            to.setSheared(from.isSheared());
        }

        sw.spawnEntity(healthy);
        corrupted.discard(); // removed, not killed: the chunk remembers nothing

        sw.playSound(null, healthy.getBlockPos(), SoundEvents.ENTITY_ZOMBIE_VILLAGER_CURE,
                SoundCategory.NEUTRAL, 1.0F, 1.0F);
        sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
                healthy.getX(), healthy.getBodyY(0.5), healthy.getZ(), 16, 0.4, 0.4, 0.4, 0.0);
    }
}
