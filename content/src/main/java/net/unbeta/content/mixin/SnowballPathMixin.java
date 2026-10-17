package net.unbeta.content.mixin;

import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.entity.projectile.thrown.ThrownEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.unbeta.content.snowball.Snowballs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fire, torches and the like have no collision box, so a snowball would sail straight through
 * them. Before it moves, check the stretch it's about to fly: the first burning thing on it is
 * put out and the snowball bursts there.
 */
@Mixin(ThrownEntity.class)
public abstract class SnowballPathMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void unbeta_snuffOnTheWay(CallbackInfo ci) {
        if (!((Object) this instanceof SnowballEntity self) || !(self.getWorld() instanceof ServerWorld world)) return;
        Vec3d from = self.getPos();
        Vec3d to = from.add(self.getVelocity());
        HitResult wall = world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, self));
        if (wall.getType() != HitResult.Type.MISS) to = wall.getPos();
        if (Snowballs.sweep(world, self, from, to)) {
            world.sendEntityStatus(self, (byte) 3);   // snowball burst particles
            self.discard();
            ci.cancel();
        }
    }
}
