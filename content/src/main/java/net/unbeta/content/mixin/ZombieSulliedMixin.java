package net.unbeta.content.mixin;

import net.unbeta.content.zombie.SulliedChunkState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class ZombieSulliedMixin {

    @Inject(method = "onDeath", at = @At("TAIL"))
    private void unbeta_sullyChunk(DamageSource source, CallbackInfo ci) {
        if (!((Object)this instanceof ZombieEntity zombie)) return;
        if (zombie.getWorld().isClient) return;
        ServerWorld world = (ServerWorld) zombie.getWorld();
        ChunkPos chunkPos = new ChunkPos(zombie.getBlockPos());
        SulliedChunkState.getOrCreate(world).sully(chunkPos, world.getTime());
    }
}
