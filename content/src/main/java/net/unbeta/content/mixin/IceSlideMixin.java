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
 * still, a push gets you going; after that it's mostly momentum.
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
    /** The fastest anything slides (blocks a tick; a sprint is about 0.28). */
    @Unique private static final double TOP = 0.45;

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
        return s >= 0.97F ? GLIDE : s;
    }

    @Inject(method = "travel", at = @At("TAIL"))
    private void unbeta_topSpeedOnIce(Vec3d input, CallbackInfo ci) {
        if (!unbeta_onIce()) return;
        Vec3d v = this.getVelocity();
        double h = v.horizontalLength();
        if (h > TOP) this.setVelocity(v.x * TOP / h, v.y, v.z * TOP / h);
    }
}
