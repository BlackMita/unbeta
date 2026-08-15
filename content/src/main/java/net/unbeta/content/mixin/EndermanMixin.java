package net.unbeta.content.mixin;

import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
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
        if (self.getTarget() == null) return;
        if (MobLightAwareness.mobOrTargetInLight(self)) {
            self.setTarget(null);
            teleportRandomly();
        }
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
