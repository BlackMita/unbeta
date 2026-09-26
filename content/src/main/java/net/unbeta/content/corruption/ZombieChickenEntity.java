package net.unbeta.content.corruption;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.Items;

/** A chicken taken by zombie corruption. */
public class ZombieChickenEntity extends ChickenEntity implements CorruptedAnimal {

    public ZombieChickenEntity(EntityType<? extends ChickenEntity> type, World world) {
        super(type, world);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        // Twice a vanilla chicken's 0.25.
        return CorruptedAnimal.hostile(ChickenEntity.createChickenAttributes(), 1.0)
                .add(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.5);
    }

    /**
     * Let vanilla build its goals first - some animals initialise fields there that
     * they read every tick (the sheep's grass-eating goal would otherwise crash it) -
     * then throw them all out, tempt goals added by our own mixins included, and
     * install zombie AI instead.
     */
    @Override
    protected void initGoals() {
        super.initGoals();
        this.goalSelector.clear(g -> true);
        this.targetSelector.clear(g -> true);
        CorruptedAnimal.addHostileGoals(this, this.goalSelector, this.targetSelector);
    }

    /** Only a golden apple does anything: it cures. No milking, shearing, saddling or breeding. */
    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        return CorruptionCure.tryFeed(this, player, hand);
    }

    @Override
    public boolean isBreedingItem(ItemStack stack) {
        return false;
    }

    @Override
    protected boolean isDisallowedInPeaceful() {
        return true;
    }

    @Override
    public SoundCategory getSoundCategory() {
        return SoundCategory.HOSTILE;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_ZOMBIE_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_ZOMBIE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_ZOMBIE_DEATH;
    }

    /** Zombie voice pitched to the body it's in. */
    @Override
    public float getSoundPitch() {
        return super.getSoundPitch() * 1.5F;
    }

    /** A corrupted hen lays nothing. Vanilla's egg timer calls dropItem(EGG) and plays a plop. */
    @Override
    public ItemEntity dropItem(ItemConvertible item) {
        if (item.asItem() == Items.EGG) return null;
        return super.dropItem(item);
    }

    @Override
    public void playSound(SoundEvent sound, float volume, float pitch) {
        if (sound == SoundEvents.ENTITY_CHICKEN_EGG) return;
        super.playSound(sound, volume, pitch);
    }
}
