package net.unbeta.content.goldreath;

import net.minecraft.block.Blocks;
import net.minecraft.block.LightBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.GhastEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.Structure;
import net.unbeta.content.skyhold.SkyholdSpace;

/**
 * A Goldreath: a gilded, winged Ghast at 3/4 size and half health, glowing like glowstone.
 *
 * <p>Inside a Skyhold's footprint it is hostile: it fires fat shulker-style bolts (strong
 * Levitation), targets players at any height and calls lightning on anyone beneath it.
 *
 * <p>Anywhere else it is a GUIDE: silent, never attacking, drifting gently toward the nearest
 * Skyhold while a player is within 45 blocks. Its height wanders - now and then it floats up far
 * enough to lose you, meanders, and sinks back of its own accord. Reaching the footprint, it
 * turns hostile. Hit while a guide, it shrieks and bolts straight up, vanishing above y 330.
 */
public class GoldreathEntity extends GhastEntity {

    public static final TagKey<Structure> SKYHOLDS = TagKey.of(RegistryKeys.STRUCTURE, new Identifier("unbeta-content", "skyholds"));

    private static final int LIGHTNING_COOLDOWN = 100;   // ticks
    private static final double UNDER_RADIUS = 4.0;      // blocks, horizontally
    private static final double UNDER_DEPTH = 48.0;      // blocks below it
    private static final double GUIDE_RANGE = 45.0;      // a player this close is being guided
    private static final double GUIDE_STEP = 10.0;       // blocks toward the Skyhold per move

    private int lightningCooldown;
    private BlockPos lightPos;
    private BlockPos skyholdTarget;
    private int locateCooldown;
    private boolean guide;
    private boolean modeKnown;
    private boolean fleeing;

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

    public boolean isGuide() {
        return guide;
    }

    public void setSkyholdTarget(BlockPos pos) {
        this.skyholdTarget = pos;
    }

    /** A guide (or a fleeing one) never takes a target - so it never shoots. */
    @Override
    public void setTarget(LivingEntity target) {
        super.setTarget(guide || fleeing ? null : target);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.getWorld() instanceof ServerWorld world)) return;
        if (this.age % 4 == 0) updateLight(world);
        if (fleeing) {
            flee();
            return;
        }
        if (!modeKnown || this.age % 20 == 0) updateMode(world);
        if (guide) {
            if (this.age % 40 == 0) guideStep(world);
            return;
        }
        if (lightningCooldown > 0) lightningCooldown--;
        else if (this.age % 10 == 0) strikeBelow(world);
    }

    /** Hostile inside a Skyhold's footprint, a silent guide everywhere else. */
    private void updateMode(ServerWorld world) {
        boolean g = !SkyholdSpace.inFootprint(world, this.getBlockPos());
        modeKnown = true;
        if (g == guide && this.isSilent() == g) return;
        guide = g;
        this.setSilent(g);
        if (g) super.setTarget(null);
    }

    private void guideStep(ServerWorld world) {
        Random r = this.getRandom();
        PlayerEntity player = world.getClosestPlayer(this, GUIDE_RANGE);
        if (player != null && skyholdTarget == null && --locateCooldown <= 0) {
            skyholdTarget = world.locateStructure(SKYHOLDS, this.getBlockPos(), 64, false);
            locateCooldown = 15;   // x 40 ticks: try again in 30 s if nothing was found
        }
        int ground = world.getTopY(Heightmap.Type.MOTION_BLOCKING, this.getBlockX(), this.getBlockZ());

        if (player != null && skyholdTarget != null) {
            // Guiding: drift toward the Skyhold, bobbing - and now and then floating up sharply.
            double dx = skyholdTarget.getX() + 0.5 - this.getX(), dz = skyholdTarget.getZ() + 0.5 - this.getZ();
            double len = Math.max(1.0, Math.sqrt(dx * dx + dz * dz));
            double step = Math.min(GUIDE_STEP, len);
            double y = this.getY() + (r.nextInt(6) == 0 ? 8 + r.nextInt(14) : r.nextInt(7) - 3);
            y = MathHelper.clamp(y, ground + 4, 300);
            this.getMoveControl().moveTo(this.getX() + dx / len * step, y, this.getZ() + dz / len * step, 0.5);
        } else if (r.nextInt(3) == 0) {
            // Lost its player: meander, sinking back toward a comfortable height above the ground.
            double y = this.getY() + MathHelper.clamp((ground + 12) - this.getY(), -6.0, 6.0) + r.nextInt(3) - 1;
            this.getMoveControl().moveTo(this.getX() + r.nextInt(17) - 8, y, this.getZ() + r.nextInt(17) - 8, 0.4);
        }
    }

    /** Hit while peaceful: a pain shriek, and it bolts straight up and away. */
    @Override
    public boolean damage(DamageSource source, float amount) {
        if (guide && !fleeing && !this.getWorld().isClient && !this.isInvulnerableTo(source)) {
            fleeing = true;
            super.setTarget(null);
            this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.ENTITY_GHAST_HURT, this.getSoundCategory(), 4.0F, 1.0F);
        }
        return super.damage(source, amount);
    }

    /** Rising fast; gone once well above the build limit. */
    private void flee() {
        Vec3d v = this.getVelocity().multiply(0.8, 1.0, 0.8).add(0.0, 0.12, 0.0);
        this.setVelocity(v.x, Math.min(v.y, 1.2), v.z);
        this.getMoveControl().moveTo(this.getX(), this.getY() + 30, this.getZ(), 1.0);
        if (this.getY() > 330) this.discard();
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
        if (skyholdTarget != null) nbt.putLong("UnbetaSkyhold", skyholdTarget.asLong());
        if (fleeing) nbt.putBoolean("UnbetaFleeing", true);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("UnbetaLight")) lightPos = BlockPos.fromLong(nbt.getLong("UnbetaLight"));
        if (nbt.contains("UnbetaSkyhold")) skyholdTarget = BlockPos.fromLong(nbt.getLong("UnbetaSkyhold"));
        fleeing = nbt.getBoolean("UnbetaFleeing");
    }
}
