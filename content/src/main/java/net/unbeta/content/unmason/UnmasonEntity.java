package net.unbeta.content.unmason;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;

import java.util.Random;

public class UnmasonEntity extends ZombieEntity {

    public UnmasonEntity(EntityType<? extends ZombieEntity> type, World world) {
        super(type, world);
        this.setPersistent();
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return ZombieEntity.createZombieAttributes()
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.23); // gentle wander speed
    }

    @Override
    protected void initGoals() {
        // Never attacks. Highest priority first, one slot each - goals at equal priority
        // can't interrupt each other, which is why seeking and wandering must not share one.
        this.goalSelector.add(0, new UnmasonRepairGoal(this));         // a breach outranks everything, even being watched
        this.goalSelector.add(1, new UnmasonFreezeGoal(this));         // inside: holds still under a player's gaze
        this.goalSelector.add(2, new UnmasonFollowGoal(this));         // inside: follows whoever last looked at it
        this.goalSelector.add(3, new UnmasonSeekStrongholdGoal(this)); // outside: heads for the stronghold
        this.goalSelector.add(4, new WanderAroundFarGoal(this, 0.6));
        this.goalSelector.add(5, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F));
        this.goalSelector.add(6, new LookAroundGoal(this));
    }

    @Override
    protected boolean canConvertInWater() { return false; }

    @Override protected net.minecraft.sound.SoundEvent getAmbientSound() { return net.minecraft.sound.SoundEvents.INTENTIONALLY_EMPTY; }
    @Override protected net.minecraft.sound.SoundEvent getHurtSound(net.minecraft.entity.damage.DamageSource source) { return net.minecraft.sound.SoundEvents.INTENTIONALLY_EMPTY; }
    @Override protected net.minecraft.sound.SoundEvent getDeathSound() { return net.minecraft.sound.SoundEvents.INTENTIONALLY_EMPTY; }
    @Override protected net.minecraft.sound.SoundEvent getStepSound() { return net.minecraft.sound.SoundEvents.INTENTIONALLY_EMPTY; }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient) return;

        // --- Mining Fatigue aura ---
        // Every 3 seconds, refresh a 5-second Mining Fatigue on every player within 16
        // blocks. The refresh interval is shorter than the duration, so the effect is
        // continuous while in range and lapses on its own shortly after leaving.
        //
        // The chat notice fires only when the player does NOT already have the effect.
        // That single check does all the anti-spam work: the refresh keeps it applied,
        // so neither this Unmason nor any other will message again until it has lapsed.
        if (this.age % 60 == 0) {
            for (net.minecraft.server.network.ServerPlayerEntity p
                    : ((net.minecraft.server.world.ServerWorld) this.getWorld()).getPlayers()) {
                if (p.isSpectator() || p.isCreative()) continue;
                if (p.squaredDistanceTo(this) > 16.0 * 16.0) continue;

                if (!p.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.MINING_FATIGUE)) {
                    p.sendMessage(net.minecraft.text.Text.literal("A nearby Unmason has inflicted ")
                            .formatted(net.minecraft.util.Formatting.GRAY)
                            .append(net.minecraft.text.Text.literal("Mining Fatigue II")
                                    .formatted(net.minecraft.util.Formatting.RED))
                            .append(net.minecraft.text.Text.literal(" upon you!")
                                    .formatted(net.minecraft.util.Formatting.GRAY)), false);
                }
                p.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                        net.minecraft.entity.effect.StatusEffects.MINING_FATIGUE,
                        100,    // 5 seconds
                        1,      // Mining Fatigue II (9% mining speed)
                        false,  // not ambient
                        true)); // show particles
            }
        }
        var speedAttr = this.getAttributeInstance(
                net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speedAttr == null) return;
        java.util.UUID waterUuid = java.util.UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
        net.minecraft.entity.attribute.EntityAttributeModifier waterBoost =
            new net.minecraft.entity.attribute.EntityAttributeModifier(
                waterUuid, "unbeta_unmason_water_speed", 1.0,
                net.minecraft.entity.attribute.EntityAttributeModifier.Operation.MULTIPLY_BASE);
        if (this.isTouchingWater()) {
            if (!speedAttr.hasModifier(waterBoost)) speedAttr.addTemporaryModifier(waterBoost);
        } else {
            speedAttr.removeModifier(waterBoost);
        }
    }

    @Override
    protected boolean burnsInDaylight() { return false; }

    @Override
    protected void dropEquipment(net.minecraft.entity.damage.DamageSource source,
                                  int lootingMultiplier, boolean allowDrops) {
        // Drop 1 stone bricks or 1 mossy stone bricks
        ItemStack drop = this.getRandom().nextBoolean()
                ? new ItemStack(Items.STONE_BRICKS)
                : new ItemStack(Items.MOSSY_STONE_BRICKS);
        this.dropStack(drop);
    }

    @Override
    protected void dropLoot(net.minecraft.entity.damage.DamageSource source, boolean causedByPlayer) {
        // Override to drop nothing from loot table — our dropEquipment handles it
    }

    public static boolean canSpawn(EntityType<UnmasonEntity> type, ServerWorldAccess world,
                                    SpawnReason reason, BlockPos pos,
                                    net.minecraft.util.math.random.Random random) {
        // Never an Unmason inside a Skyhold - a zombie there stays a zombie.
        if (net.unbeta.content.skyhold.SkyholdSpace.isInside(world.toServerWorld(), pos)) return false;
        return true;
    }

    /**
     * Unmasons are the stronghold's caretakers, not monsters: Peaceful doesn't delete them.
     * (Otherwise a breach in Peaceful raises a mason, Peaceful removes it a tick later, and the
     * repair system raises another every second, forever.) Harmless there anyway - Peaceful
     * zeroes monster damage to players. Their ordinary despawning is unchanged.
     */
    @Override
    protected boolean isDisallowedInPeaceful() {
        return false;
    }
}
