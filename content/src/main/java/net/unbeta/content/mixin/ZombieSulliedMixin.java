package net.unbeta.content.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.unbeta.content.corruption.CorruptionMemory;
import net.unbeta.content.zombie.SulliedChunkState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every death that corruption can leave behind: a zombie (as a zombie), a corrupted animal
 * (as itself), a bitten animal (as its corrupted form). Burning deaths and gold sword/axe
 * kills are clean and leave nothing. See CorruptionMemory for the rules.
 */
@Mixin(LivingEntity.class)
public abstract class ZombieSulliedMixin {

    @Inject(method = "onDeath", at = @At("TAIL"))
    private void unbeta_sullyChunk(DamageSource source, CallbackInfo ci) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (self.getWorld().isClient) return;

        String remembered = CorruptionMemory.rememberedAs(self);
        if (remembered == null) return;
        if (CorruptionMemory.isCleanDeath(self, source)) return;

        SulliedChunkState.getOrCreate((ServerWorld) self.getWorld())
                .remember(new ChunkPos(self.getBlockPos()), remembered);
    }
}
