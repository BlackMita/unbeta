package net.unbeta.content.guardianpost;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.mob.GuardianEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.World;
import net.unbeta.content.obsidiandoor.XpHelper;
import net.unbeta.content.xpdrain.XpDrain;

/**
 * The Guardian Post: a 1 x 3 obsidian post topped with a tailless Guardian that never moves.
 *
 * <ul>
 *   <li>It turns slowly round and round, scanning its room. When its gaze crosses a player
 *       it can see, it locks on and follows them until they get out of its sight.</li>
 *   <li>Locked on, it charges and fires a Guardian's beam (same wind-up): 3 hearts that no
 *       armour, shield or enchantment softens - and the blast knocks 3 levels of experience
 *       out of the player, which scatter and are sucked into the Post.</li>
 *   <li>It judges: it only bothers with players who have MORE experience than it holds. Once
 *       it is the richer of the two, it ignores them and goes back to scanning.</li>
 *   <li>It can't be killed. Every hit (melee or arrow, from a player) knocks a fifth of its
 *       hoard back out as ordinary orbs for the taking.</li>
 * </ul>
 */
public class GuardianPostEntity extends GuardianEntity {

    public static final RegistryKey<DamageType> BEAM = RegistryKey.of(RegistryKeys.DAMAGE_TYPE,
            new Identifier("unbeta-content", "post_beam"));

    private static final double SIGHT = 24.0;
    private static final float SWEEP = 1.5F;          // degrees per tick: a full turn every 12 seconds
    private static final float CONE = 10.0F;          // half-width of its gaze, degrees
    private static final float BEAM_DAMAGE = 6.0F;    // 3 hearts
    private static final int DRAIN_LEVELS = 3;
    private static final int COOLDOWN = 40;           // ticks between beams on the same player
    private static final int LOSE_AFTER = 6;          // ticks out of sight before it lets go
    private static final float HEAD = 2.45F;          // eye height: the Guardian on top of the post

    private int storedXp;
    private boolean anchored;
    private double anchorX, anchorY, anchorZ;
    private float sweepYaw;
    private int spin = 1;                             // +1 clockwise, -1 anticlockwise
    private PlayerEntity target;
    private int unseen, charge, cooldown, hitCooldown;

    private final XpDrain.Sink sink = new XpDrain.Sink() {
        @Override
        public Vec3d target() {
            return GuardianPostEntity.this.getPos().add(0, HEAD, 0);
        }

        @Override
        public boolean alive() {
            return GuardianPostEntity.this.isAlive() && !GuardianPostEntity.this.isRemoved();
        }

        @Override
        public void absorb(int points) {
            storedXp += points;
        }
    };

    public GuardianPostEntity(EntityType<? extends GuardianPostEntity> type, World world) {
        super(type, world);
        this.setNoGravity(true);
        this.experiencePoints = 0;
        this.storedXp = 20 + this.random.nextInt(81);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, SIGHT)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0);
    }

    public int storedXp() {
        return storedXp;
    }

    // ------------------------------------------------------------------ a post, not a fish

    @Override
    protected void initGoals() {
        // No goals: it doesn't wander, and its gaze and beam are run by tick().
    }

    @Override
    public boolean isOnGround() {
        return false;   // so the Guardian never "flops" about on land
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPushedByFluids() {
        return false;
    }

    @Override
    public boolean isCollidable() {
        return true;    // solid, like a block
    }

    @Override
    public void takeKnockback(double strength, double x, double z) {
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    @Override
    public boolean cannotDespawn() {
        return true;
    }

    @Override
    protected boolean isDisallowedInPeaceful() {
        return false;
    }

    @Override
    protected float getActiveEyeHeight(EntityPose pose, EntityDimensions dimensions) {
        return HEAD;
    }

    // ------------------------------------------------------------------ unkillable

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.damage(source, amount);   // /kill
        if (this.getWorld().isClient) return false;
        Entity attacker = source.getAttacker();
        if (attacker instanceof PlayerEntity player && !player.isSpectator() && hitCooldown <= 0) {
            hitCooldown = 10;
            release();
        }
        return false;
    }

    /** Struck: a fifth of its hoard bursts out as ordinary orbs. */
    private void release() {
        ServerWorld world = (ServerWorld) this.getWorld();
        world.playSound(null, getX(), getY() + HEAD, getZ(), SoundEvents.ENTITY_GUARDIAN_HURT_LAND,
                SoundCategory.HOSTILE, 1.0F, 0.6F + random.nextFloat() * 0.2F);
        int out = storedXp <= 0 ? 0 : Math.max(1, storedXp / 5);
        storedXp -= out;
        int pieces = Math.max(1, Math.min(8, out / 3));
        int each = Math.max(1, out / pieces);
        while (out > 0) {
            int value = Math.min(out, each);
            out -= value;
            ExperienceOrbEntity orb = new ExperienceOrbEntity(world, getX(), getY() + HEAD, getZ(), value);
            double a = random.nextDouble() * Math.PI * 2;
            orb.setVelocity(Math.cos(a) * 0.25, 0.2 + random.nextDouble() * 0.15, Math.sin(a) * 0.25);
            world.spawnEntity(orb);
        }
    }

    // ------------------------------------------------------------------ the gaze

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient) return;
        if (!anchored) {
            anchored = true;
            anchorX = getX();
            anchorY = getY();
            anchorZ = getZ();
            sweepYaw = getYaw();
        }
        this.setVelocity(Vec3d.ZERO);
        if (getX() != anchorX || getY() != anchorY || getZ() != anchorZ) this.setPosition(anchorX, anchorY, anchorZ);
        if (hitCooldown > 0) hitCooldown--;
        if (cooldown > 0) cooldown--;
        think((ServerWorld) this.getWorld());
    }

    /** Worth judging: alive, playing, close, and holding more experience than this Post does. */
    private boolean judges(PlayerEntity p) {
        return p.isAlive() && !p.isSpectator() && !p.isCreative()
                && p.getWorld() == this.getWorld()
                && p.getWorld().getDifficulty() != Difficulty.PEACEFUL
                && p.squaredDistanceTo(this.getX(), this.getEyeY(), this.getZ()) <= SIGHT * SIGHT
                && XpHelper.totalPoints(p) > storedXp;
    }

    private void think(ServerWorld world) {
        if (target != null && !judges(target)) letGo();
        if (target != null) {
            unseen = this.canSee(target) ? 0 : unseen + 1;
            if (unseen > LOSE_AFTER) letGo();
        }
        if (target == null) {
            sweepYaw = MathHelper.wrapDegrees(sweepYaw + SWEEP * spin);
            face(sweepYaw, 0.0F);
            for (PlayerEntity p : world.getPlayers()) {
                if (!judges(p)) continue;
                float bearing = yawTo(p);
                if (Math.abs(MathHelper.wrapDegrees(bearing - sweepYaw)) <= CONE && this.canSee(p)) {
                    target = p;
                    unseen = 0;
                    charge = 0;
                    break;
                }
            }
            return;
        }

        // Locked on: follow them with its gaze, and charge while they stay in sight.
        double dx = target.getX() - getX(), dz = target.getZ() - getZ();
        double dy = target.getEyeY() - getEyeY();
        float pitch = (float) -(MathHelper.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * (180.0 / Math.PI));
        sweepYaw = yawTo(target);
        face(sweepYaw, pitch);
        if (unseen > 0 || cooldown > 0) {
            if (charge > 0) {
                charge = 0;
                beam(0);
            }
            return;
        }
        if (charge == 0) beam(target.getId());
        if (++charge >= this.getWarmupTime()) {
            fire(world, target);
            charge = 0;
            cooldown = COOLDOWN;
            spin = random.nextBoolean() ? 1 : -1;   // after a shot it may sweep back the other way
            beam(0);
        }
    }

    private void beam(int targetId) {
        ((net.unbeta.content.mixin.GuardianBeamInvoker) (Object) this).unbeta$setBeamTarget(targetId);
    }

    private void letGo() {
        target = null;
        unseen = 0;
        if (charge > 0) beam(0);
        charge = 0;
    }

    private float yawTo(Entity e) {
        return MathHelper.wrapDegrees((float) (MathHelper.atan2(e.getZ() - getZ(), e.getX() - getX()) * (180.0 / Math.PI)) - 90.0F);
    }

    private void face(float yaw, float pitch) {
        this.setYaw(yaw);
        this.setHeadYaw(yaw);
        this.setBodyYaw(yaw);
        this.setPitch(pitch);
    }

    /** The beam lands: 3 hearts straight through any armour, and 3 levels blasted out into the Post. */
    private void fire(ServerWorld world, PlayerEntity player) {
        DamageSource source = new DamageSource(
                world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(BEAM), this);
        player.damage(source, BEAM_DAMAGE);
        int level = player.experienceLevel;
        int points = XpHelper.pointsForLevel(level) - XpHelper.pointsForLevel(Math.max(0, level - DRAIN_LEVELS));
        if (points <= 0) points = XpHelper.totalPoints(player);
        XpDrain.drain(world, player, points, sink, 0.55, 6, 0.7);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE,
                SoundCategory.HOSTILE, 0.5F, 1.6F);
    }

    // ------------------------------------------------------------------ saving

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("StoredXp", storedXp);
        nbt.putBoolean("Anchored", anchored);
        nbt.putDouble("AnchorX", anchorX);
        nbt.putDouble("AnchorY", anchorY);
        nbt.putDouble("AnchorZ", anchorZ);
        nbt.putFloat("Sweep", sweepYaw);
        nbt.putInt("Spin", spin);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("StoredXp")) storedXp = nbt.getInt("StoredXp");
        anchored = nbt.getBoolean("Anchored");
        anchorX = nbt.getDouble("AnchorX");
        anchorY = nbt.getDouble("AnchorY");
        anchorZ = nbt.getDouble("AnchorZ");
        sweepYaw = nbt.getFloat("Sweep");
        spin = nbt.getInt("Spin") < 0 ? -1 : 1;
    }
}
