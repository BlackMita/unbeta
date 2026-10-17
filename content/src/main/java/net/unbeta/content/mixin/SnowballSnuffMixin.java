package net.unbeta.content.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.unbeta.content.snowball.Snowballs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Snowballs put out burning things they hit (see Snowballs). */
@Mixin(SnowballEntity.class)
public abstract class SnowballSnuffMixin {

    @Inject(method = "onEntityHit", at = @At("HEAD"))
    private void unbeta_douseEntity(EntityHitResult hit, CallbackInfo ci) {
        Entity target = hit.getEntity();
        if (!target.getWorld().isClient && target.isOnFire()) target.extinguishWithSound();
    }

    @Inject(method = "onCollision", at = @At("HEAD"))
    private void unbeta_douseBlock(HitResult hit, CallbackInfo ci) {
        SnowballEntity self = (SnowballEntity) (Object) this;
        if (!(hit instanceof BlockHitResult block) || !(self.getWorld() instanceof ServerWorld world)) return;
        BlockPos pos = block.getBlockPos();
        // The block itself (a Jack o'Lantern, a hot Burnt block), whatever burns on top of it,
        // and whatever burns against the face it hit.
        if (!Snowballs.snuff(world, pos) && !Snowballs.snuff(world, pos.up())) Snowballs.snuff(world, pos.offset(block.getSide()));
    }
}
