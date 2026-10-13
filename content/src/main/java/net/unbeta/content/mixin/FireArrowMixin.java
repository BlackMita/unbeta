package net.unbeta.content.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.unbeta.content.arrow.FireArrowAccess;
import net.unbeta.content.arrow.FireArrows;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fire arrows - see FireArrows. Adds the synced "charred" flag the renderer reads. */
@Mixin(PersistentProjectileEntity.class)
public abstract class FireArrowMixin implements FireArrowAccess {

    @Unique
    private static final TrackedData<Boolean> UNBETA_CHARRED =
            DataTracker.registerData(PersistentProjectileEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    @Shadow protected boolean inGround;

    @Unique private int unbeta_fireColor = FireArrows.NO_COLOR;
    @Unique private int unbeta_burningInGround = 0;

    @Override
    public boolean unbeta$isCharred() {
        return ((Entity) (Object) this).getDataTracker().get(UNBETA_CHARRED);
    }

    @Inject(method = "initDataTracker", at = @At("TAIL"))
    private void unbeta_trackCharred(CallbackInfo ci) {
        ((Entity) (Object) this).getDataTracker().startTracking(UNBETA_CHARRED, false);
    }

    /** Before the arrow moves: check every block on the stretch it is about to fly. */
    @Inject(method = "tick", at = @At("HEAD"))
    private void unbeta_sweepFlightPath(CallbackInfo ci) {
        PersistentProjectileEntity self = (PersistentProjectileEntity) (Object) this;
        if (!(self.getWorld() instanceof ServerWorld world) || inGround || unbeta$isCharred() || self.isNoClip()) return;
        Vec3d from = self.getPos();
        Vec3d to = from.add(self.getVelocity());
        HitResult wall = world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, self));
        if (wall.getType() != HitResult.Type.MISS) to = wall.getPos();
        unbeta_fireColor = FireArrows.sweep(world, self, from, to, unbeta_fireColor);
    }

    /** Landed still burning: a few ticks later it burns out. */
    @Inject(method = "tick", at = @At("RETURN"))
    private void unbeta_burnOut(CallbackInfo ci) {
        PersistentProjectileEntity self = (PersistentProjectileEntity) (Object) this;
        if (!(self.getWorld() instanceof ServerWorld world) || self.isRemoved() || unbeta$isCharred()) return;
        if (!inGround || !self.isOnFire()) {
            unbeta_burningInGround = 0;
            return;
        }
        if (++unbeta_burningInGround < FireArrows.CHAR_AFTER_TICKS) return;
        self.extinguish();
        self.getDataTracker().set(UNBETA_CHARRED, true);
        self.pickupType = PersistentProjectileEntity.PickupPermission.DISALLOWED;
        unbeta_fireColor = FireArrows.NO_COLOR;
        world.playSound(null, self.getX(), self.getY(), self.getZ(), SoundEvents.BLOCK_FIRE_EXTINGUISH,
                SoundCategory.NEUTRAL, 0.3F, 1.8F);
    }

    @Inject(method = "onBlockHit", at = @At("HEAD"))
    private void unbeta_burningHit(BlockHitResult hit, CallbackInfo ci) {
        PersistentProjectileEntity self = (PersistentProjectileEntity) (Object) this;
        if (self.getWorld() instanceof ServerWorld world && !unbeta$isCharred()) {
            FireArrows.onBlockHit(world, self, hit, unbeta_fireColor);
        }
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void unbeta_saveFire(NbtCompound nbt, CallbackInfo ci) {
        if (unbeta$isCharred()) nbt.putBoolean("UnbetaCharred", true);
        if (unbeta_fireColor != FireArrows.NO_COLOR) nbt.putInt("UnbetaFireColor", unbeta_fireColor);
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void unbeta_loadFire(NbtCompound nbt, CallbackInfo ci) {
        if (nbt.getBoolean("UnbetaCharred")) ((Entity) (Object) this).getDataTracker().set(UNBETA_CHARRED, true);
        unbeta_fireColor = nbt.contains("UnbetaFireColor") ? nbt.getInt("UnbetaFireColor") : FireArrows.NO_COLOR;
    }
}
