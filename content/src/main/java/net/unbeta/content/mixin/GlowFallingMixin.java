package net.unbeta.content.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.unbeta.content.glow.Glow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A glowing block that falls (sand, gravel...) is still glowing where it lands. */
@Mixin(FallingBlockEntity.class)
public abstract class GlowFallingMixin {

    private static final String UNBETA_TAG = "unbeta.glowing";
    private static final ThreadLocal<Boolean> UNBETA_WAS = new ThreadLocal<>();

    @Inject(method = "spawnFromBlock", at = @At("HEAD"))
    private static void unbeta_glowFallStart(World world, BlockPos pos, BlockState state, CallbackInfoReturnable<FallingBlockEntity> cir) {
        UNBETA_WAS.set(world instanceof ServerWorld sw && Glow.glows(sw, pos.asLong()));
    }

    @Inject(method = "spawnFromBlock", at = @At("RETURN"))
    private static void unbeta_glowFallTag(World world, BlockPos pos, BlockState state, CallbackInfoReturnable<FallingBlockEntity> cir) {
        if (Boolean.TRUE.equals(UNBETA_WAS.get()) && cir.getReturnValue() != null) cir.getReturnValue().addCommandTag(UNBETA_TAG);
        UNBETA_WAS.remove();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void unbeta_glowLanded(CallbackInfo ci) {
        FallingBlockEntity self = (FallingBlockEntity) (Object) this;
        if (!self.isRemoved() || !(self.getWorld() instanceof ServerWorld sw) || !self.getCommandTags().contains(UNBETA_TAG)) return;
        self.getCommandTags().remove(UNBETA_TAG);
        BlockPos at = self.getBlockPos();
        if (sw.getBlockState(at).isOf(self.getBlockState().getBlock())) Glow.add(sw, at);
    }
}
