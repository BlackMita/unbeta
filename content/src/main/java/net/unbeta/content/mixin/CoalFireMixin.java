package net.unbeta.content.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FireBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.unbeta.content.gunpowder.Gunpowder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * When ordinary fire appears:
 * <ul>
 *   <li>any gunpowder fuse beside it (corners too) catches;</li>
 *   <li>on top of a Coal Block it stays ordinary fire and burns for ever, as on netherrack (the
 *       Burnt mod would otherwise swap it for its own fire, which soon dies).</li>
 * </ul>
 * Runs before Burnt's own hook on the same method (lower priority number goes first).
 */
@Mixin(value = FireBlock.class, priority = 500)
public abstract class CoalFireMixin {

    @Inject(method = "onBlockAdded", at = @At("HEAD"), cancellable = true)
    private void unbeta_coalFire(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify, CallbackInfo ci) {
        if (world.isClient || oldState.isOf(state.getBlock())) return;
        if (world instanceof ServerWorld sw) Gunpowder.fireNear(sw, pos);
        if (!world.getBlockState(pos.down()).isOf(Blocks.COAL_BLOCK)) return;
        // What FireBlock does when placed (wait, then burn), without anything after it.
        world.scheduleBlockTick(pos, (FireBlock) (Object) this, 30 + world.random.nextInt(10));
        ci.cancel();
    }
}
