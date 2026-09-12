package net.unbeta.content.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Replaces PORTAL particles with navy dust for endermen in LivingEntity.handleStatus
 * (the teleport poof) and the idle particle loop.
 */
@Mixin(LivingEntity.class)
public class EndermanTeleportParticleMixin {

    private static final Vector3f NAVY = Vec3d.unpackRgb(0x1B2A6B).toVector3f();

    @ModifyArg(
        method = "handleStatus",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/world/World;addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)V"),
        index = 0
    )
    private ParticleEffect unbeta_navyTeleportPoof(ParticleEffect original) {
        // Only recolor if this entity is an enderman
        if (!((Object)this instanceof EndermanEntity)) return original;
        if (original == ParticleTypes.PORTAL) {
            return new DustParticleEffect(NAVY, 1.0F);
        }
        return original;
    }
}
