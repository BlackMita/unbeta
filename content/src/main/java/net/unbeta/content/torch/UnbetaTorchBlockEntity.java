package net.unbeta.content.torch;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * BLOCK-side torch state, mirroring TorchItems' one rule:
 * LIT -> burnoutAt (absolute world time) is truth; UNLIT -> frozenRemaining is truth.
 * Conversion happens only at a lit/unlit transition, never on place/mine, so burn-life
 * carries across those without drift.
 */
public class UnbetaTorchBlockEntity extends BlockEntity {

    public static final long DEFAULT_BURN_TICKS = 4800L; // 4 minutes

    private long burnoutAt = -1L;                 // valid when lit
    private long frozenRemaining = DEFAULT_BURN_TICKS; // valid when unlit
    private long full = DEFAULT_BURN_TICKS;       // bar denominator

    public UnbetaTorchBlockEntity(BlockPos pos, BlockState state) {
        super(UnbetaTorchRegistry.TORCH_BLOCK_ENTITY, pos, state);
    }

    public long getFull() { return full; }
    public long getBurnoutAt() { return burnoutAt; }

    /** True remaining ticks right now. */
    public long getRemainingTicks() {
        if (burnoutAt >= 0 && world != null) {
            return Math.max(0, burnoutAt - world.getTime());
        }
        return frozenRemaining;
    }

    /** Adopt state from a placed item (verbatim - no re-anchoring). */
    public void adoptFromItem(boolean lit, long itemBurnoutAt, long itemRemaining, long itemFull) {
        this.full = itemFull > 0 ? itemFull : DEFAULT_BURN_TICKS;
        if (lit && itemBurnoutAt >= 0) {
            this.burnoutAt = itemBurnoutAt;     // carry the SAME deadline
            this.frozenRemaining = DEFAULT_BURN_TICKS;
        } else {
            this.burnoutAt = -1L;
            this.frozenRemaining = itemRemaining > 0 ? itemRemaining : DEFAULT_BURN_TICKS;
        }
        markDirty();
    }

    /** Light: frozen remaining -> absolute deadline, and schedule the burnout event. */
    public void onLit(World world, BlockPos pos) {
        long remaining = (burnoutAt >= 0) ? getRemainingTicks() : frozenRemaining;
        if (remaining <= 0) remaining = DEFAULT_BURN_TICKS;
        this.burnoutAt = world.getTime() + remaining;
        if (world instanceof net.minecraft.server.world.ServerWorld sw) {
            net.unbeta.core.sched.UnbetaScheduler.schedule(sw, pos, remaining, TorchBurnout.HANDLER_ID);
        }
        markDirty();
    }

    /** Extinguish: absolute deadline -> frozen remaining. */
    public void onExtinguished() {
        long remaining = getRemainingTicks();
        this.frozenRemaining = (remaining > 0) ? remaining : DEFAULT_BURN_TICKS;
        this.burnoutAt = -1L;
        markDirty();
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putLong("BurnoutAt", burnoutAt);
        nbt.putLong("FrozenRemaining", frozenRemaining);
        nbt.putLong("Full", full);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        if (nbt.contains("BurnoutAt")) burnoutAt = nbt.getLong("BurnoutAt");
        if (nbt.contains("FrozenRemaining")) frozenRemaining = nbt.getLong("FrozenRemaining");
        if (nbt.contains("Full")) full = nbt.getLong("Full");
    }
}
