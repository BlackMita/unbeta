package net.unbeta.core.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.NetherPortalBlock;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.unbeta.core.gates.DimensionGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * INERT IN PHASE 1 - and that is the whole point.
 *
 * <p>Beta 1.8 has the Nether, so {@code dimension/minecraft.the_nether} resolves to
 * "not removed" and this injection does nothing. Phase 2's Unbeta 1.7.3 removes the
 * Nether by flipping that one rule in ContentRules.java, at which point this mixin
 * starts cancelling traversal with no code change anywhere.
 *
 * <p>Building it now, unused, is cheaper than retrofitting it later.
 */
@Mixin(NetherPortalBlock.class)
public abstract class NetherPortalBlockMixin {

    @Inject(method = "onEntityCollision", at = @At("HEAD"), cancellable = true)
    private void unbeta$blockNetherTravel(BlockState state, World world, BlockPos pos,
                                          Entity entity, CallbackInfo ci) {
        if (DimensionGate.isGated(World.NETHER)) {
            ci.cancel();
        }
    }
}
