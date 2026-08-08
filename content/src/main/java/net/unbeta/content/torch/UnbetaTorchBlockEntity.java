package net.unbeta.content.torch;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

/**
 * Stores a placed torch's remaining burn-life (in ticks). When lit, it schedules a
 * burnout via the core scheduler's POSITION callback ("go unlit here in N ticks"). The
 * remaining life persists in NBT so it survives save/load, and is read back when a torch
 * is placed from an item carrying leftover life.
 *
 * <p>Design note: we lean on the scheduler for the actual timing rather than ticking the
 * block entity ourselves, so a placed torch in an unloaded chunk simply pauses (the
 * scheduler task rides in per-world persistent state) - matching the item behaviour.
 */
public class UnbetaTorchBlockEntity extends BlockEntity {

    /** Default full burn-life for a freshly-lit torch: 4 minutes (4800 ticks). */
    public static final long DEFAULT_BURN_TICKS = 4800L;

    private long remainingTicks = DEFAULT_BURN_TICKS;

    public UnbetaTorchBlockEntity(BlockPos pos, BlockState state) {
        super(UnbetaTorchRegistry.TORCH_BLOCK_ENTITY, pos, state);
    }

    public long getRemainingTicks() { return remainingTicks; }

    public void setRemainingTicks(long ticks) {
        this.remainingTicks = Math.max(0, ticks);
        markDirty();
    }

    /** Called when the placed torch is lit. Schedules its burnout. */
    public void onLit(net.minecraft.world.World world, BlockPos pos) {
        if (remainingTicks <= 0) remainingTicks = DEFAULT_BURN_TICKS;
        if (world instanceof net.minecraft.server.world.ServerWorld sw) {
            net.unbeta.core.sched.UnbetaScheduler.schedule(
                    sw, pos, remainingTicks, TorchBurnout.HANDLER_ID);
        }
        markDirty();
    }

    public void onExtinguished() {
        // Torch went out by player action; keep remainingTicks as-is (re-lightable).
        // The scheduled burnout, if any, will find the torch already unlit and no-op.
        markDirty();
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putLong("BurnTicks", remainingTicks);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        if (nbt.contains("BurnTicks")) remainingTicks = nbt.getLong("BurnTicks");
    }
}
