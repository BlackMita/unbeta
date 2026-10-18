package net.unbeta.content.mixin;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.unbeta.content.light.Beacons;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hostile mobs go for anyone (or any scarecrow) wearing a lit Jack o'Lantern - see Beacons. */
@Mixin(MobEntity.class)
public abstract class BeaconMobMixin {

    @Inject(method = "mobTick", at = @At("TAIL"))
    private void unbeta_seekBeacon(CallbackInfo ci) {
        MobEntity self = (MobEntity) (Object) this;
        if (self.getWorld() instanceof ServerWorld world) Beacons.tickMob(world, self);
    }
}
