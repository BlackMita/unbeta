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
        // Unmasons are passive stone-masonry mobs, not true zombies - they never sully.
        if (zombie instanceof net.unbeta.content.unmason.UnmasonEntity) return;
        if (zombie.getWorld().isClient) return;
        ServerWorld world = (ServerWorld) zombie.getWorld();
        ChunkPos chunkPos = new ChunkPos(zombie.getBlockPos());
        // Gold sword/axe kill = clean kill: this death isn't remembered. The chunk's
        // other remembered deaths are unaffected and still rise.
        var attacker = source.getAttacker();
        if (attacker instanceof net.minecraft.entity.player.PlayerEntity player
                && (player.getMainHandStack().isOf(net.minecraft.item.Items.GOLDEN_SWORD)
                || player.getMainHandStack().isOf(net.minecraft.item.Items.GOLDEN_AXE))) {
            return;
        }
        // Every zombie variant is remembered as a plain zombie, as before.
        SulliedChunkState.getOrCreate(world).remember(chunkPos, SulliedChunkState.ZOMBIE);
    }
}
