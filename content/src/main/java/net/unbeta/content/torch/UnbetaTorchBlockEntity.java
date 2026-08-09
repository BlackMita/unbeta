package net.unbeta.content.torch;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class UnbetaTorchBlockEntity extends BlockEntity {

    public static final long DEFAULT_BURN_TICKS = 4800L; // 4 minutes

    private long burnoutAt = -1L;
    private long full = DEFAULT_BURN_TICKS;

    public UnbetaTorchBlockEntity(BlockPos pos, BlockState state) {
        super(UnbetaTorchRegistry.TORCH_BLOCK_ENTITY, pos, state);
    }

    public long getFull() { return full; }
    public long getBurnoutAt() { return burnoutAt; }

    public long getRemainingTicks() {
        if (burnoutAt < 0 || world == null) return 0L;
        return Math.max(0L, burnoutAt - world.getTime());
    }

    /** Light: ALWAYS a full burn. */
    public void light(World world, BlockPos pos) {
        this.burnoutAt = world.getTime() + this.full;
        if (world instanceof net.minecraft.server.world.ServerWorld sw) {
            net.unbeta.core.sched.UnbetaScheduler.schedule(sw, pos, this.full, TorchBurnout.HANDLER_ID);
        }
        markDirty();
    }

    /** Extinguish: stop the clock. Remaining discarded. */
    public void extinguish() {
        this.burnoutAt = -1L;
        markDirty();
    }

    /** Adopt a placed item's state verbatim. */
    public void adopt(boolean lit, long itemBurnoutAt, long itemFull) {
        this.full = itemFull > 0 ? itemFull : DEFAULT_BURN_TICKS;
        this.burnoutAt = (lit && itemBurnoutAt >= 0) ? itemBurnoutAt : -1L;
        markDirty();
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putLong("BurnoutAt", burnoutAt);
        nbt.putLong("Full", full);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        if (nbt.contains("BurnoutAt")) burnoutAt = nbt.getLong("BurnoutAt");
        if (nbt.contains("Full")) full = nbt.getLong("Full");
    }
}
