package net.unbeta.content.mixin;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ice is all but frictionless, for players and mobs alike (the old ice-puzzle feel): once you
 * are sliding you keep going, and your feet can barely steer, speed up or slow down. Standing
 * still, a push gets you going; after that it's mostly momentum. No walking: legs keep still
 * and footsteps are silent, as you glide. Sprinting, nothing is lost to the ice at all, so you
 * go faster and faster.
 *
 * <p>Ice here is any block as slippery as ice or more (ice, packed ice, blue ice, frosted ice).
 * Runs on both sides, as a player's own game works out their movement.
 */
@Mixin(LivingEntity.class)
public abstract class IceSlideMixin extends Entity {

    /** Keeps 99% of its speed each tick on the ground (ordinary ice keeps about 89%). */
    @Unique private static final float GLIDE = 0.99F / 0.91F;
    /** How much of a push its feet get on ice once it is moving (1 = all of it). */
    @Unique private static final double GRIP = 0.17;
    /** Below this speed (blocks a tick) it can still push off properly. */
    @Unique private static final double PUSH_OFF = 0.05;
    /** Sprinting: keeps all its speed, so its push adds up and up. */
    @Unique private static final float GLIDE_SPRINT = 1.0F / 0.91F;
    /** Faster than this, not sprinting, the ice slows it a little more (blocks a tick; a sprint on land is about 0.28). */
    @Unique private static final double TOP = 0.45;
    /** Faster than this it never goes, sprinting or not (40 blocks a second). */
    @Unique private static final double LIMIT = 2.0;

    protected IceSlideMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @Unique
    private boolean unbeta_onIce() {
        return this.isOnGround() && this.getWorld().getBlockState(this.getVelocityAffectingPos()).getBlock().getSlipperiness() >= 0.97F;
    }

    @ModifyVariable(method = "travel", at = @At("HEAD"), argsOnly = true)
    private Vec3d unbeta_weakFeetOnIce(Vec3d input) {
        if (!unbeta_onIce() || this.getVelocity().horizontalLength() < PUSH_OFF) return input;
        return new Vec3d(input.x * GRIP, input.y, input.z * GRIP);
    }

    @Redirect(method = "travel", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;getSlipperiness()F"))
    private float unbeta_glide(Block block) {
        float s = block.getSlipperiness();
        return s < 0.97F ? s : this.isSprinting() ? GLIDE_SPRINT : GLIDE;
    }

    @Inject(method = "travel", at = @At("TAIL"))
    private void unbeta_topSpeedOnIce(Vec3d input, CallbackInfo ci) {
        if (!unbeta_onIce()) return;
        Vec3d v = this.getVelocity();
        double h = v.horizontalLength();
        double keep = 1.0;
        if (h > TOP && !this.isSprinting()) keep = Math.max(TOP / h, 0.98);    // eases back down, no sudden stop
        if (h * keep > LIMIT) keep = LIMIT / h;
        if (keep < 1.0) this.setVelocity(v.x * keep, v.y, v.z * keep);
    }

    /** Gliding on ice, its legs keep still (they settle, as when standing). */
    @Inject(method = "updateLimbs(F)V", at = @At("HEAD"), cancellable = true)
    private void unbeta_noStridesOnIce(float posDelta, CallbackInfo ci) {
        if (!unbeta_onIce()) return;
        ((LivingEntity) (Object) this).limbAnimator.updateLimbs(0.0F, 0.4F);
        ci.cancel();
    }
}
