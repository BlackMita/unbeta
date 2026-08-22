package net.unbeta.content.mixin;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.CaveSpiderEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class SpiderBurstMixin {

    private static final float BURST_CHANCE = 0.1f;
    private static final int BURST_COUNT = 8;

    @Inject(method = "onDeath", at = @At("TAIL"))
    private void unbeta_spiderBurst(DamageSource source, CallbackInfo ci) {
        if (!((Object)this instanceof SpiderEntity)) return;
        if ((Object)this instanceof CaveSpiderEntity) return;
        LivingEntity self = (LivingEntity)(Object)this;
        if (self.getWorld().isClient) return;
        ServerWorld world = (ServerWorld) self.getWorld();
        if (world.random.nextFloat() >= BURST_CHANCE) return;
        Vec3d pos = self.getPos();
        for (int i = 0; i < BURST_COUNT; i++) {
            CaveSpiderEntity baby = EntityType.CAVE_SPIDER.create(world);
            if (baby == null) continue;
            double dx = (world.random.nextDouble() - 0.5) * 1.0;
            double dz = (world.random.nextDouble() - 0.5) * 1.0;
            baby.refreshPositionAndAngles(
                    pos.x + dx, pos.y, pos.z + dz,
                    world.random.nextFloat() * 360.0F, 0.0F);
            baby.initialize(world,
                    world.getLocalDifficulty(baby.getBlockPos()),
                    SpawnReason.MOB_SUMMONED, null, null);
            baby.setPersistent();
            baby.setVelocity(0, 0.1, 0);
            world.spawnEntity(baby);
        }
    }
}
