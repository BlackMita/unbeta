package net.unbeta.content.jackolantern;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.unbeta.core.sched.UnbetaScheduler;

public final class JackOLanternBurnout {

    public static final String HANDLER_ID = "unbeta:jol_burnout";

    private JackOLanternBurnout() {}

    public static void register() {
        UnbetaScheduler.registerPositionHandler(HANDLER_ID, JackOLanternBurnout::onBurnout);
    }

    private static void onBurnout(ServerWorld world, BlockPos pos, NbtCompound payload) {
        var state = world.getBlockState(pos);
        boolean ours = state.getBlock() instanceof UnbetaJackOLanternBlock
                || state.getBlock() instanceof UnbetaLitJackOLanternBlock;
        if (!ours || !state.get(JackOLanternLogic.LIT)) return;
        if (world.getBlockEntity(pos) instanceof JackOLanternBlockEntity be
                && be.getRemainingTicks() > 0) return; // stale task
        JackOLanternLogic.extinguishPlaced(world, pos, state);
    }
}
