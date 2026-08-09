package net.unbeta.content.torch;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.unbeta.core.sched.UnbetaScheduler;

public final class TorchBurnout {

    public static final String HANDLER_ID = "unbeta:torch_burnout";

    private TorchBurnout() {}

    public static void register() {
        UnbetaScheduler.registerPositionHandler(HANDLER_ID, TorchBurnout::onBurnout);
    }

    private static void onBurnout(ServerWorld world, BlockPos pos, NbtCompound payload) {
        var state = world.getBlockState(pos);
        boolean ours = state.getBlock() instanceof UnbetaTorchBlock
                || state.getBlock() instanceof UnbetaWallTorchBlock;
        if (!ours || !state.get(TorchLogic.LIT)) return;
        if (world.getBlockEntity(pos) instanceof UnbetaTorchBlockEntity be
                && be.getRemainingTicks() > 0) return; // stale task
        TorchLogic.extinguishPlaced(world, pos, state);
    }
}
