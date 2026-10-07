package net.unbeta.content.goldreath;

import net.minecraft.block.Blocks;
import net.minecraft.block.LightBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.GhastEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * A Goldreath: a gilded, winged Ghast at 3/4 size and half health. It shoots fat, fast shulker
 * bullets (strong Levitation) instead of fireballs, glows like glowstone, targets players at any
 * height, and calls lightning down on anyone standing beneath it.
 */
public class GoldreathEntity extends GhastEntity {

    private static final int LIGHTNING_COOLDOWN = 100;   // ticks
    private static final double UNDER_RADIUS = 4.0;      // blocks, horizontally
    private static final double UNDER_DEPTH = 48.0;      // blocks below it

    private int lightningCooldown;
    private BlockPos lightPos;

    public GoldreathEntity(EntityType<? extends GhastEntity> type, World world) {
        super(type, world);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return GhastEntity.createGhastAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 5.0);
    }

    @Override
    protected void initGoals() {
        super.initGoals();
        // A Ghast only targets players within 4 blocks of its own height; a Goldreath, anyone in range.
        this.targetSelector.clear(goal -> true);
        this.targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, 10, true, false, p -> true));
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.getWorld() instanceof ServerWorld world)) return;
        if (this.age % 4 == 0) updateLight(world);
        if (lightningCooldown > 0) lightningCooldown--;
        else if (this.age % 10 == 0) strikeBelow(world);
    }

    private void strikeBelow(ServerWorld world) {
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (p.isSpectator()) continue;
            double dx = p.getX() - this.getX(), dz = p.getZ() - this.getZ();
            double below = this.getY() - p.getY();
            if (dx * dx + dz * dz > UNDER_RADIUS * UNDER_RADIUS || below < 1 || below > UNDER_DEPTH) continue;
            LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
            if (bolt == null) return;
            bolt.refreshPositionAfterTeleport(p.getX(), p.getY(), p.getZ());
            world.spawnEntity(bolt);
            lightningCooldown = LIGHTNING_COOLDOWN;
            return;
        }
    }

    /** Carries a level-15 light (glowstone's) along with it, using an invisible light block. */
    private void updateLight(ServerWorld world) {
        BlockPos here = BlockPos.ofFloored(this.getX(), this.getBodyY(0.5), this.getZ());
        if (here.equals(lightPos)) return;
        clearLight(world);
        if (world.isAir(here)) {
            world.setBlockState(here, Blocks.LIGHT.getDefaultState().with(LightBlock.LEVEL_15, 15), 3);
            lightPos = here;
        }
    }

    private void clearLight(World world) {
        if (lightPos != null && world.getBlockState(lightPos).isOf(Blocks.LIGHT)) {
            world.setBlockState(lightPos, Blocks.AIR.getDefaultState(), 3);
        }
        lightPos = null;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (reason.shouldDestroy() && !this.getWorld().isClient) clearLight(this.getWorld());
        super.remove(reason);
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (lightPos != null) nbt.putLong("UnbetaLight", lightPos.asLong());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("UnbetaLight")) lightPos = BlockPos.fromLong(nbt.getLong("UnbetaLight"));
    }
}
