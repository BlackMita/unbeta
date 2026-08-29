package net.unbeta.content.unlikelike;

import net.minecraft.entity.*;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.ai.pathing.AmphibiousSwimNavigation;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.biome.BiomeKeys;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.math.random.Random;

/**
 * Unlike-Like — a slow, gelatinous hostile mob that grabs players,
 * blinds them, and steals their equipment before spitting them out.
 *
 * Phase A: movement, navigation, basic AI, spawn conditions.
 * Phase B: grab mechanic (riding + blindness + spit).
 * Phase C: theft cascade + digest timer.
 * Phase D: custom model.
 * Phase E: custom sounds.
 */
public class UnlikeLikeEntity extends HostileEntity {

    /** Ticks the grab lasts before spitting the player out. */
    public static final int GRAB_DURATION = 40; // 2 seconds

    /** Ticks after spitting before the stolen item is digested (gone forever). */
    public static final int DIGEST_DURATION = 1200; // 60 seconds

    private int grabTimer = 0;
    private int digestTimer = 0;
    private boolean isGrabbing = false;
    private ItemStack stolenItem = ItemStack.EMPTY;

    public UnlikeLikeEntity(EntityType<? extends UnlikeLikeEntity> type, World world) {
        super(type, world);
        this.setStepHeight(0.5F); // can only climb half-blocks; full blocks blocked
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 40.0)        // 20 HP = zombie; 40 = twice
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.18)    // slow, but accelerates
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 3.0)      // weak spit damage
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void initGoals() {
        // Priority 1: grab and hold player when adjacent
        this.goalSelector.add(1, new UnlikeLikeGrabGoal(this));
        // Priority 2: swim toward target
        this.goalSelector.add(2, new MoveIntoWaterGoal(this));
        // Priority 3: melee walk toward target
        this.goalSelector.add(3, new MeleeAttackGoal(this, 1.0, true));
        // Priority 4: wander
        this.goalSelector.add(4, new WanderAroundFarGoal(this, 0.8));
        // Priority 5: look at player
        this.goalSelector.add(5, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F));

        // Target selectors
        this.targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
    }

    @Override
    protected EntityNavigation createNavigation(World world) {
        return new AmphibiousSwimNavigation(this, world);
    }

    @Override
    public void tick() {
        super.tick();
        // Underwater: allow jumping over full blocks
        if (this.isSubmergedInWater()) {
            this.setStepHeight(1.0F);
        } else {
            this.setStepHeight(0.5F);
        }

        if (!this.getWorld().isClient) {
            handleGrab();
            handleDigest();
        }
    }

    private void handleGrab() {
        if (!isGrabbing) return;
        grabTimer--;
        if (grabTimer <= 0) {
            spitOut();
        }
    }

    private void handleDigest() {
        if (stolenItem.isEmpty()) return;
        digestTimer--;
        if (digestTimer <= 0) {
            stolenItem = ItemStack.EMPTY; // digested — gone forever
        }
    }

    public void startGrab(PlayerEntity player) {
        if (isGrabbing) return;
        isGrabbing = true;
        grabTimer = GRAB_DURATION;
        // Phase B will implement riding + blindness here
    }

    private void spitOut() {
        isGrabbing = false;
        grabTimer = 0;
        // Phase B will implement spit knockback + theft here
    }

    // --- NBT (persist stolen item and timers across chunk unload) ---

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putBoolean("IsGrabbing", isGrabbing);
        nbt.putInt("GrabTimer", grabTimer);
        nbt.putInt("DigestTimer", digestTimer);
        if (!stolenItem.isEmpty()) {
            nbt.put("StolenItem", stolenItem.writeNbt(new NbtCompound()));
        }
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        isGrabbing = nbt.getBoolean("IsGrabbing");
        grabTimer = nbt.getInt("GrabTimer");
        digestTimer = nbt.getInt("DigestTimer");
        if (nbt.contains("StolenItem")) {
            stolenItem = ItemStack.fromNbt(nbt.getCompound("StolenItem"));
        }
    }

    // --- Spawn conditions ---

    public static boolean canSpawn(EntityType<UnlikeLikeEntity> type, ServerWorldAccess world,
                                   SpawnReason reason, BlockPos pos, Random random) {
        // Standard light level check
        if (!HostileEntity.canSpawnInDark(type, world, reason, pos, random)) return false;

        int y = pos.getY();

        if (y >= 0) {
            // Surface: must be in an ocean or swamp biome
            var biome = world.getBiome(pos);
            boolean isOcean = biome.isIn(net.minecraft.registry.tag.BiomeTags.IS_OCEAN);
            boolean isSwamp = biome.matchesKey(BiomeKeys.SWAMP)
                    || biome.matchesKey(net.minecraft.registry.RegistryKey.of(
                            net.minecraft.registry.RegistryKeys.BIOME,
                            new net.minecraft.util.Identifier("moderner_beta", "beta_swampland")));
            return isOcean || isSwamp;
        } else {
            // Underground: must be within 16 blocks of a water source
            BlockPos.Mutable check = pos.mutableCopy();
            for (int dx = -8; dx <= 8; dx++) {
                for (int dz = -8; dz <= 8; dz++) {
                    for (int dy = -4; dy <= 4; dy++) {
                        check.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                        if (world.getFluidState(check).isOf(net.minecraft.fluid.Fluids.WATER)
                                && world.getFluidState(check).isStill()) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }
    }

    @Override
    protected void jump() {
        // Only jump underwater — on land, Unlike-Likes cannot ascend full blocks.
        if (this.isSubmergedInWater() || this.isTouchingWater()) {
            super.jump();
        }
    }

    @Override
    public boolean canBreatheInWater() { return true; }

}
