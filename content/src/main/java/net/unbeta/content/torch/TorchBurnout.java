package net.unbeta.content.torch;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.unbeta.core.sched.UnbetaScheduler;

/**
 * Registers the scheduler position-handler that turns a placed lit torch unlit when its
 * burn-life expires. Registered once at content init.
 */
public final class TorchBurnout {

    public static final String HANDLER_ID = "unbeta:torch_burnout";

    private TorchBurnout() {}

    public static void register() {
        UnbetaScheduler.registerPositionHandler(HANDLER_ID, TorchBurnout::onBurnout);
    }

    private static void onBurnout(ServerWorld world, BlockPos pos, NbtCompound payload) {
        var state = world.getBlockState(pos);
        // Only act if it is still one of our lit torches.
        if (state.getBlock() instanceof UnbetaTorchBlock && state.get(UnbetaTorchBlock.LIT)) {
            UnbetaTorchBlock.extinguish(world, pos, state);
        } else if (state.getBlock() instanceof UnbetaWallTorchBlock && state.get(UnbetaWallTorchBlock.LIT)) {
            world.setBlockState(pos, state.with(UnbetaWallTorchBlock.LIT, false), 3);
        }
        // Reset the block entity's remaining life to full so it can be re-lit later.
        if (world.getBlockEntity(pos) instanceof UnbetaTorchBlockEntity be) {
            be.onExtinguished(); // freezes remaining and clears the deadline (relightable)
        }
    }
}
