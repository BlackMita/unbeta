package net.unbeta.content.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A locked chest will not merge into a double chest.
 *
 * <p>Without this, placing a fresh chest beside a locked single chest produced a double
 * chest whose unlocked half opened the shared inventory - a way into a locked chest that
 * never touched the key.
 *
 * <p>Blocked from both directions: the new chest must not choose to pair with a locked
 * neighbour, and an existing locked chest must not convert itself when a neighbour appears.
 */
@Mixin(ChestBlock.class)
public class LockedChestNoMergeMixin {

    /** A newly placed chest must not pair with a locked neighbour. */
    @Inject(method = "getPlacementState", at = @At("RETURN"), cancellable = true)
    private void unbeta_noMergeOnPlace(ItemPlacementContext ctx,
                                       CallbackInfoReturnable<BlockState> cir) {
        BlockState result = cir.getReturnValue();
        if (result == null) return;
        if (result.get(ChestBlock.CHEST_TYPE) == ChestType.SINGLE) return;
        if (!(ctx.getWorld() instanceof net.minecraft.server.world.ServerWorld sw)) return;

        BlockPos partner = ctx.getBlockPos().offset(ChestBlock.getFacing(result));
        if (net.unbeta.content.lockey.LockeyState.isLocked(sw, partner)) {
            cir.setReturnValue(result.with(ChestBlock.CHEST_TYPE, ChestType.SINGLE));
        }
    }

    /** A locked chest must not convert itself to a double when a neighbour appears. */
    @Inject(method = "getStateForNeighborUpdate", at = @At("RETURN"), cancellable = true)
    private void unbeta_noMergeOnNeighbor(BlockState state, Direction direction,
                                          BlockState neighborState, WorldAccess world,
                                          BlockPos pos, BlockPos neighborPos,
                                          CallbackInfoReturnable<BlockState> cir) {
        BlockState result = cir.getReturnValue();
        if (result == null) return;
        if (result.get(ChestBlock.CHEST_TYPE) == ChestType.SINGLE) return;
        if (state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE) return; // already double
        if (!(world instanceof net.minecraft.server.world.ServerWorld sw)) return;

        if (net.unbeta.content.lockey.LockeyState.isLocked(sw, pos)) {
            cir.setReturnValue(state);
        }
    }
}
