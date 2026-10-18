package net.unbeta.content.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.unbeta.content.xpdrain.XpDrain;
import net.unbeta.content.xpdrain.XpOrbAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Claimed experience orbs - see XpDrain. The "claimed" flag is synced so clients fly them too. */
@Mixin(ExperienceOrbEntity.class)
public abstract class XpOrbClaimMixin implements XpOrbAccess {

    @Unique
    private static final TrackedData<Boolean> UNBETA_CLAIMED =
            DataTracker.registerData(ExperienceOrbEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    @Unique private XpDrain.Sink unbeta_sink;
    @Unique private int unbeta_burstTicks;
    @Unique private double unbeta_speed;
    @Unique private int unbeta_flight;

    @Override
    public void unbeta$claim(XpDrain.Sink sink, int burstTicks, double speed) {
        unbeta_sink = sink;
        unbeta_burstTicks = burstTicks;
        unbeta_speed = speed;
        unbeta_flight = 0;
        ((Entity) (Object) this).getDataTracker().set(UNBETA_CLAIMED, true);
    }

    @Override
    public boolean unbeta$isClaimed() {
        return ((Entity) (Object) this).getDataTracker().get(UNBETA_CLAIMED);
    }

    @Inject(method = "initDataTracker", at = @At("TAIL"))
    private void unbeta_trackClaimed(CallbackInfo ci) {
        ((Entity) (Object) this).getDataTracker().startTracking(UNBETA_CLAIMED, false);
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void unbeta_fly(CallbackInfo ci) {
        if (!unbeta$isClaimed()) return;
        ci.cancel();
        ExperienceOrbEntity self = (ExperienceOrbEntity) (Object) this;
        Vec3d v = self.getVelocity();
        if (self.getWorld().isClient) {
            self.setPosition(self.getPos().add(v));
            return;
        }
        if (unbeta_sink == null || !unbeta_sink.alive() || ++unbeta_flight > 100) {
            self.discard();
            return;
        }
        Vec3d to = unbeta_sink.target().subtract(self.getPos());
        double dist = to.length();
        if (unbeta_flight > unbeta_burstTicks) {
            if (dist <= Math.max(0.6, unbeta_speed)) {
                unbeta_sink.absorb(self.getExperienceAmount());
                self.getWorld().playSound(null, unbeta_sink.target().x, unbeta_sink.target().y, unbeta_sink.target().z,
                        SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.NEUTRAL, 0.2F,
                        0.5F + self.getWorld().random.nextFloat() * 0.4F);
                self.discard();
                return;
            }
            Vec3d want = to.multiply(Math.min(unbeta_speed, dist) / dist);
            v = v.lerp(want, 0.45);
        } else {
            v = v.multiply(0.8);
        }
        self.setVelocity(v);
        self.setPosition(self.getPos().add(v));
        self.velocityModified = true;
    }

    @Inject(method = "onPlayerCollision", at = @At("HEAD"), cancellable = true)
    private void unbeta_untouchable(PlayerEntity player, CallbackInfo ci) {
        if (unbeta$isClaimed()) ci.cancel();
    }

    @Inject(method = "merge", at = @At("HEAD"), cancellable = true, require = 0)
    private void unbeta_noMerge(ExperienceOrbEntity other, CallbackInfo ci) {
        if (unbeta$isClaimed() || ((XpOrbAccess) other).unbeta$isClaimed()) ci.cancel();
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void unbeta_saveClaimed(NbtCompound nbt, CallbackInfo ci) {
        if (unbeta$isClaimed()) nbt.putBoolean("UnbetaClaimed", true);
    }

    /** Saved mid-flight: its sink is long gone, so on its first tick it simply disappears. */
    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void unbeta_loadClaimed(NbtCompound nbt, CallbackInfo ci) {
        if (nbt.getBoolean("UnbetaClaimed")) ((Entity) (Object) this).getDataTracker().set(UNBETA_CLAIMED, true);
    }
}
