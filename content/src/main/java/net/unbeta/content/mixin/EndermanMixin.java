package net.unbeta.content.mixin;

import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.Vec3d;
import net.unbeta.content.mob.MobLightAwareness;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Unbeta enderman rework:
 * - Eyes/particles recoloured blue-navy (Ender Pearl palette).
 * - No water damage.
 * - Light-based protection: an aggroed enderman standing in light >=9, OR whose target
 *   is in light >=9, teleports AWAY instead of attacking (takes no damage - just leaves).
 * - Daylight burning removed (the vanilla daylight-teleport in mobTick still runs, but
 *   our light check fires first and covers the "sun is up" case anyway).
 */
@Mixin(EndermanEntity.class)
public abstract class EndermanMixin {

    @Shadow protected abstract boolean teleportRandomly();

    /** Ender-Pearl navy. Packed RGB 0x1B2A6B -> navy blue. */
    private static final Vector3f UNBETA_NAVY = Vec3d.unpackRgb(0x1B2A6B).toVector3f();

    /** Clear the vanilla water-avoidance pathfinding penalty (endermen no longer fear water). */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void unbeta_allowWaterPathing(net.minecraft.entity.EntityType<? extends EndermanEntity> type,
                                          net.minecraft.world.World world, CallbackInfo ci) {
        ((PathAwareEntity)(Object)this).setPathfindingPenalty(PathNodeType.WATER, 0.0F);
    }


    /** No water damage. */
    @Inject(method = "hurtByWater", at = @At("HEAD"), cancellable = true)
    private void unbeta_noWaterDamage(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }

    /**
     * Light-based protection. Runs at the head of mobTick: if this enderman has a target
     * and either it or the target is in light >=9, drop the target and teleport away.
     */
    @Inject(method = "mobTick", at = @At("HEAD"))
    private void unbeta_lightTeleport(CallbackInfo ci) {
        EndermanEntity self = (EndermanEntity)(Object)this;
        if (self.getWorld().isClient) return;

        // Light protection: if aggroed and mob/target in light, flee
        // Held glow berries count as deterrent — treat player as lit
        if (self.getTarget() instanceof net.minecraft.entity.player.PlayerEntity tp) {
            boolean holdingGlowBerries =
                tp.getMainHandStack().isOf(net.minecraft.item.Items.GLOW_BERRIES)
                || tp.getOffHandStack().isOf(net.minecraft.item.Items.GLOW_BERRIES);
            if (holdingGlowBerries) {
                self.setTarget(null);
                teleportRandomly();
                return;
            }
        }
        if (self.getTarget() != null && MobLightAwareness.mobOrTargetInLight(self)) {
            self.setTarget(null);
            teleportRandomly();
            return;
        }

        // Sunlight instakill: direct sky light = 15 AND daytime = instant death
        if (!self.getWorld().isClient) {
            int skyLight = self.getWorld().getLightLevel(
                    net.minecraft.world.LightType.SKY, self.getBlockPos());
            boolean isDaytime = self.getWorld().isDay();
            if (skyLight >= 15 && isDaytime) {
                self.setHealth(0);
                self.kill();
                return;
            }
        }

        // Water speed boost: double movement speed when in water
        net.minecraft.entity.attribute.EntityAttributeInstance speedAttr =
            self.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speedAttr != null) {
            net.minecraft.entity.attribute.EntityAttributeModifier waterBoost =
                new net.minecraft.entity.attribute.EntityAttributeModifier(
                    java.util.UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890"),
                    "unbeta_water_speed", 1.0, // +100% = double speed
                    net.minecraft.entity.attribute.EntityAttributeModifier.Operation.MULTIPLY_BASE);
            if (self.isTouchingWater()) {
                if (!speedAttr.hasModifier(waterBoost)) speedAttr.addTemporaryModifier(waterBoost);
            } else {
                speedAttr.removeModifier(waterBoost);
            }
        }

        // Proximity aggro: any player within 24 blocks triggers hostility
        if (self.getTarget() == null) {
            PlayerEntity closest = self.getWorld().getClosestPlayer(
                    self.getX(), self.getY(), self.getZ(), 24.0, true);
            if (closest != null) {
                self.setTarget(closest);
            }
        }
    }

    /**
     * Unlimited-range stare aggro: if a player is looking directly at this enderman
     * from any distance, aggro immediately. Vanilla already handles this but only
     * within a limited range — we override the range check.
     */
    @Inject(method = "isPlayerStaring", at = @At("HEAD"), cancellable = true)
    private void unbeta_unlimitedStare(PlayerEntity player,
                                        CallbackInfoReturnable<Boolean> cir) {
        EndermanEntity self = (EndermanEntity)(Object)this;
        Vec3d look = player.getRotationVec(1.0F).normalize();
        Vec3d toEnderman = new Vec3d(
                self.getX() - player.getX(),
                self.getEyeY() - player.getEyeY(),
                self.getZ() - player.getZ());
        double dist = toEnderman.length();
        if (dist < 0.001) { cir.setReturnValue(false); return; }
        Vec3d dir = toEnderman.normalize();
        double dot = look.dotProduct(dir);
        // dot > 0.99 means player is looking almost directly at the enderman
        cir.setReturnValue(dot > 0.99);
    }

    /**
     * Invulnerability: endermen take no HP damage. Still play hurt sound and get
     * knocked back so attacks feel responsive, but health never changes.
     */
    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void unbeta_invulnerable(DamageSource source, float amount,
                                      CallbackInfoReturnable<Boolean> cir) {
        EndermanEntity self = (EndermanEntity)(Object)this;
        if (self.getWorld().isClient) { cir.setReturnValue(false); return; }
        // Play hurt sound and apply knockback manually
        self.playSound(SoundEvents.ENTITY_ENDERMAN_HURT, 1.0F, 1.0F);
        // Knockback toward attacker
        if (source.getAttacker() != null) {
            double dx = self.getX() - source.getAttacker().getX();
            double dz = self.getZ() - source.getAttacker().getZ();
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len > 0.001) {
                self.setVelocity(self.getVelocity().add(dx / len * 0.4, 0.1, dz / len * 0.4));
            }
        }
        cir.setReturnValue(false); // cancel damage
    }

    /**
     * Recolour the idle/teleport particles. tickMovement spawns ParticleTypes.PORTAL;
     * we swap the ParticleEffect argument for navy dust. ModifyArg targets the
     * ParticleEffect parameter of the addParticle(...) call.
     */
    @ModifyArg(
        method = "tickMovement",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/world/World;addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)V"),
        index = 0
    )
    private ParticleEffect unbeta_navyParticles(ParticleEffect original) {
        if (original == ParticleTypes.PORTAL) {
            return new DustParticleEffect(UNBETA_NAVY, 1.0F);
        }
        return original;
    }
}
