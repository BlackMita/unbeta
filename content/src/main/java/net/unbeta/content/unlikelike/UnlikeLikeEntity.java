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
import net.minecraft.util.ActionResult;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.*;
import java.util.ArrayList;
import java.util.List;

public class UnlikeLikeEntity extends HostileEntity {

    public static final int GRAB_DURATION = 30; // 1.5 seconds — released as blindness fades

    private int grabCount = 0;
    public boolean ejecting = false;
    public int grabCooldown = 0;
    public PlayerEntity grabbedPlayer = null; // set during grab, null otherwise

    /** Force-eject all passengers bypassing all checks. */
    public void forceEjectPassengers() {
        ejecting = true;
        for (var passenger : this.getPassengerList().toArray()) {
            if (passenger instanceof net.minecraft.entity.Entity e) {
                e.stopRiding();
            }
        }
        ejecting = false;
    } // set true during programmatic spit
    private final List<ItemStack> stolenItems = new ArrayList<>();

    public UnlikeLikeEntity(EntityType<? extends UnlikeLikeEntity> type, World world) {
        super(type, world);
        this.setStepHeight(0.5F);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 40.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.18)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 2.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new UnlikeLikeGrabGoal(this));
        this.goalSelector.add(2, new MoveIntoWaterGoal(this));
        this.goalSelector.add(3, new MeleeAttackGoal(this, 1.0, true));
        this.goalSelector.add(4, new WanderAroundFarGoal(this, 0.8));
        this.goalSelector.add(5, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F));
        this.targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
    }

    @Override
    protected EntityNavigation createNavigation(World world) {
        return new AmphibiousSwimNavigation(this, world);
    }

    @Override
    public void tick() {
        super.tick();
        if (grabCooldown > 0) {
            grabCooldown--;
            // Safety net: if we still have passengers during cooldown, force eject
            if (!this.getPassengerList().isEmpty()) {
                forceEjectPassengers();
            }
        }
        // Also eject if we're dead
        if (this.isDead() && !this.getPassengerList().isEmpty()) {
            forceEjectPassengers();
        }
        // Safety: clear grabbed player if they're dead, removed, or too far away
        if (grabbedPlayer != null) {
            if (grabbedPlayer.isDead() || grabbedPlayer.isRemoved()
                    || this.squaredDistanceTo(grabbedPlayer) > 100) {
                grabbedPlayer = null;
            }
        }
        if (this.isSubmergedInWater() || this.isTouchingWater()) {
            this.setStepHeight(1.0F);
        } else {
            this.setStepHeight(0.5F);
        }
    }

    @Override
    protected void jump() {
        if (this.isSubmergedInWater() || this.isTouchingWater()) {
            super.jump();
        }
    }

    public int getGrabCount() { return grabCount; }
    public void incrementGrabCount() { grabCount++; }

    public void addStolenItem(ItemStack item) {
        this.stolenItems.add(item.copy());
    }

    @Override
    public void onDeath(DamageSource source) {
        grabbedPlayer = null; // release player on death
        forceEjectPassengers(); // eject before death so client syncs
        super.onDeath(source);
        if (!this.getWorld().isClient) {
            for (ItemStack stack : stolenItems) {
                if (!stack.isEmpty()) {
                    net.minecraft.block.Block.dropStack(
                            this.getWorld(), this.getBlockPos(), stack);
                }
            }
            stolenItems.clear();
        }
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("GrabCount", grabCount);
        NbtList list = new NbtList();
        for (ItemStack stack : stolenItems) {
            list.add(stack.writeNbt(new NbtCompound()));
        }
        nbt.put("StolenItems", list);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        grabCount = nbt.getInt("GrabCount");
        stolenItems.clear();
        if (nbt.contains("StolenItems")) {
            NbtList list = nbt.getList("StolenItems", 10);
            for (int i = 0; i < list.size(); i++) {
                stolenItems.add(ItemStack.fromNbt(list.getCompound(i)));
            }
        }
    }

    public static boolean canSpawn(EntityType<UnlikeLikeEntity> type,
                                   ServerWorldAccess world, SpawnReason reason,
                                   BlockPos pos, Random random) {
        if (!HostileEntity.canSpawnInDark(type, world, reason, pos, random)) return false;
        if (pos.getY() >= 0) {
            var biome = world.getBiome(pos);
            boolean isOcean = biome.isIn(net.minecraft.registry.tag.BiomeTags.IS_OCEAN);
            boolean isSwamp = biome.matchesKey(net.minecraft.registry.RegistryKey.of(
                    net.minecraft.registry.RegistryKeys.BIOME,
                    new net.minecraft.util.Identifier("moderner_beta", "beta_swampland")));
            return isOcean || isSwamp;
        } else {
            BlockPos.Mutable check = pos.mutableCopy();
            for (int dx = -8; dx <= 8; dx++) {
                for (int dz = -8; dz <= 8; dz++) {
                    for (int dy = -4; dy <= 4; dy++) {
                        check.set(pos.getX()+dx, pos.getY()+dy, pos.getZ()+dz);
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
    public boolean tryAttack(net.minecraft.entity.Entity target) {
        return false; // Unlike-Like never melee attacks — grab goal handles everything
    }

    @Override
    public boolean canBreatheInWater() { return true; }
}
