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
        // Only passive wandering + stronghold seeking — no attack goals
        this.goalSelector.add(1, new UnmasonSeekStrongholdGoal(this));
        this.goalSelector.add(2, new WanderAroundFarGoal(this, 0.6));
        this.goalSelector.add(3, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F));
        this.goalSelector.add(4, new LookAroundGoal(this));
        // No target goals — it never attacks
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
                    p.sendMessage(net.minecraft.text.Text.literal(
                            "A nearby Unmason has inflicted Mining Fatigue upon you!")
                            .formatted(net.minecraft.util.Formatting.RED), false);
                }
                p.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                        net.minecraft.entity.effect.StatusEffects.MINING_FATIGUE,
                        100,    // 5 seconds
                        0,      // Mining Fatigue I
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
        return true;
    }
}
