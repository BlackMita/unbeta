package net.unbeta.content.mixin;

import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Recolour ender pearl trail particles blue-navy to match the reworked enderman. */
@Mixin(EnderPearlEntity.class)
public class EnderPearlParticleMixin {

    private static final Vector3f UNBETA_NAVY = Vec3d.unpackRgb(0x1B2A6B).toVector3f();

    @ModifyArg(
        method = "tick",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/world/World;addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)V"),
        index = 0
    )
    private ParticleEffect unbeta_navyPearlTrail(ParticleEffect original) {
        if (original == ParticleTypes.PORTAL) {
            return new DustParticleEffect(UNBETA_NAVY, 1.0F);
        }
        return original;
    }
}
