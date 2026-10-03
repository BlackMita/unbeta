package net.unbeta.content.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.unbeta.content.corruption.CorruptionMemory;
import net.unbeta.content.zombie.SulliedChunkState;
import net.unbeta.content.zombie.SulliedChunkTick;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every death that corruption can leave behind: a zombie (as a zombie), a corrupted animal
 * (as itself), a bitten animal (as its corrupted form). Burning deaths and gold sword/axe
 * kills are clean and leave nothing. See CorruptionMemory for the rules. Each outcome is
 * noted for /unbeta sullied, so "why didn't that come back?" can be answered.
 */
@Mixin(LivingEntity.class)
public abstract class ZombieSulliedMixin {

    @Inject(method = "onDeath", at = @At("TAIL"))
    private void unbeta_sullyChunk(DamageSource source, CallbackInfo ci) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (self.getWorld().isClient) return;

        String remembered = CorruptionMemory.rememberedAs(self);
        if (remembered == null) return;

        ServerWorld world = (ServerWorld) self.getWorld();
        ChunkPos chunk = new ChunkPos(self.getBlockPos());

        if (CorruptionMemory.isCleanDeath(self, source)) {
            SulliedChunkTick.noteDeath(world, chunk, "CLEAN death of " + remembered + " ("
                    + (self.isOnFire() ? "it was burning" : "gold weapon") + ") - not remembered");
            return;
        }
        SulliedChunkState state = SulliedChunkState.getOrCreate(world);
        if (state.peek(chunk).size() >= SulliedChunkState.MAX_REMEMBERED) {
            SulliedChunkTick.noteDeath(world, chunk, "chunk already holds "
                    + SulliedChunkState.MAX_REMEMBERED + " - " + remembered + " NOT remembered");
            return;
        }
        state.remember(chunk, remembered);
        SulliedChunkTick.noteDeath(world, chunk, "remembered " + remembered);
    }
}
