package net.unbeta.content.mixin;

import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.unbeta.content.gunpowder.Gunpowder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A burning arrow landing on (or right beside) a gunpowder trail lights it. */
@Mixin(ProjectileEntity.class)
public abstract class GunpowderArrowMixin {

    @Inject(method = "onBlockHit", at = @At("HEAD"))
    private void unbeta_lightTrail(BlockHitResult hit, CallbackInfo ci) {
        ProjectileEntity self = (ProjectileEntity) (Object) this;
        if (!(self instanceof PersistentProjectileEntity) || !self.isOnFire() || !(self.getWorld() instanceof ServerWorld sw)) return;
        for (BlockPos p : new BlockPos[]{hit.getBlockPos().offset(hit.getSide()), hit.getBlockPos().up()}) {
            if (sw.getBlockState(p).isOf(Gunpowder.TRAIL)) Gunpowder.ignite(sw, p);
        }
    }
}
