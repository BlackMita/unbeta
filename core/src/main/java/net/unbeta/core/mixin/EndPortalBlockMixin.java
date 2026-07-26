package net.unbeta.core.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.EndPortalBlock;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.unbeta.core.gates.DimensionGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancels End portal traversal when the End is gated. The portal block still exists
 * and still renders; entities simply fall through it.
 *
 * <p>Asks {@link DimensionGate} rather than checking a hardcoded flag, so this
 * respects the full rule precedence chain including Phase 2 overrides.
 */
@Mixin(EndPortalBlock.class)
public abstract class EndPortalBlockMixin {

    @Inject(method = "onEntityCollision", at = @At("HEAD"), cancellable = true)
    private void unbeta$blockEndTravel(BlockState state, World world, BlockPos pos,
                                       Entity entity, CallbackInfo ci) {
        if (DimensionGate.isGated(World.END)) {
            ci.cancel();
        }
    }
}
