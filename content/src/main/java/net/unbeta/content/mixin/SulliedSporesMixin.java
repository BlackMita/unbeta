package net.unbeta.content.mixin;

import net.minecraft.block.Block;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.unbeta.content.client.zombie.SulliedChunksClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The client samples random nearby blocks ~1,300 times a tick to give ambient-effect
 * blocks (like mycelium) their chance to show particles. Whenever that sampler lands on
 * the surface block of a sullied chunk, the block gets exactly mycelium's odds - 1 in 10 -
 * to puff a spore. Every surface block is eligible, and the density matches a real
 * mycelium field, with no network cost per particle.
 */
@Mixin(ClientWorld.class)
public abstract class SulliedSporesMixin {

    @Inject(method = "randomBlockDisplayTick", at = @At("TAIL"))
    private void unbeta_sulliedSpores(int centerX, int centerY, int centerZ, int radius,
                                      Random random, Block block, BlockPos.Mutable pos,
                                      CallbackInfo ci) {
        if (!SulliedChunksClient.isSullied(pos.getX(), pos.getZ())) return;
        if (random.nextInt(10) != 0) return; // mycelium's own odds
        ClientWorld world = (ClientWorld)(Object)this;
        int surfaceY = SulliedChunksClient.surfaceY(world, pos.getX(), pos.getZ());
        if (pos.getY() != surfaceY) return;
        // Spawned through the particle manager rather than world.addParticle, so the particle
        // comes back and can be coloured: vanilla's spore is a dark grey (~0.2-0.3) that's
        // hard to see; ours are a pale sickly green. Still honours "Particles: Minimal".
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client.options.getParticles().getValue() == net.minecraft.client.option.ParticlesMode.MINIMAL) return;
        net.minecraft.client.particle.Particle spore = client.particleManager.addParticle(
                ParticleTypes.MYCELIUM,
                pos.getX() + random.nextDouble(), surfaceY + 1.1, pos.getZ() + random.nextDouble(),
                0.0, 0.0, 0.0);
        if (spore != null) {
            float shade = 0.9F + random.nextFloat() * 0.2F; // slight per-spore variation
            spore.setColor(0.32F * shade, 0.46F * shade, 0.28F * shade); // muted, darker than grass
        }
    }
}
