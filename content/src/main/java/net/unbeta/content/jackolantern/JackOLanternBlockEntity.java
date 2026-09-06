package net.unbeta.content.jackolantern;

import net.minecraft.block.BlockState;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class JackOLanternBlockEntity extends BlockEntity {

    private long burnoutAt = -1L;
    private long full = JackOLanternLogic.FULL_BURN_TICKS;

    public JackOLanternBlockEntity(BlockPos pos, BlockState state) {
        super(JackOLanternRegistry.BLOCK_ENTITY, pos, state);
    }

    public long getFull() { return full; }
    public long getBurnoutAt() { return burnoutAt; }

    public long getRemainingTicks() {
        if (burnoutAt < 0 || world == null) return 0L;
        return Math.max(0L, burnoutAt - world.getTime());
    }

    public void light(World world, BlockPos pos) {
        this.burnoutAt = world.getTime() + this.full;
        if (world instanceof net.minecraft.server.world.ServerWorld sw) {
            net.unbeta.core.sched.UnbetaScheduler.schedule(
                    sw, pos, this.full, JackOLanternBurnout.HANDLER_ID);
        }
        markDirty();
    }

    public void extinguish() {
        this.burnoutAt = -1L;
        markDirty();
    }

    public void adopt(boolean lit, long itemBurnoutAt, long itemFull) {
        this.full = itemFull > 0 ? itemFull : JackOLanternLogic.FULL_BURN_TICKS;
        this.burnoutAt = (lit && itemBurnoutAt >= 0) ? itemBurnoutAt : -1L;
        markDirty();
    }

    /** Re-send this BE's data to nearby clients. Safe to call every tick. */
    public void sync() {
        if (world == null || world.isClient) return;
        net.minecraft.server.world.ServerWorld sw = (net.minecraft.server.world.ServerWorld) world;
        var packet = toUpdatePacket();
        if (packet == null) return;
        sw.getChunkManager().markForUpdate(pos);
    }

    /**
     * Server ticker. Attached by both JoL blocks, so every loaded JoL runs this
     * regardless of how it came to exist (placed, lit, loaded from disk, restart).
     *
     * Re-broadcasts BE data once a second so the client can never hold a stale
     * burnoutAt -- the light-time sync alone is unreliable because the block-state
     * packet from setBlockState arrives in the same tick and rebuilds the client BE.
     *
     * Also owns the burnout deadline, replacing the scheduler as source of truth.
     */
    public static void serverTick(net.minecraft.world.World world, BlockPos pos,
                                  BlockState state, JackOLanternBlockEntity be) {
        if (world.isClient) return;
        if (!state.contains(JackOLanternLogic.LIT) || !state.get(JackOLanternLogic.LIT)) return;

        long now = world.getTime();
        long burnoutAt = be.getBurnoutAt();

        if (burnoutAt >= 0 && now >= burnoutAt) {
            JackOLanternLogic.extinguishPlaced(world, pos, state);
            return;
        }

        if (now % 20 == 0) be.sync();
    }

    @Override
    public NbtCompound toInitialChunkDataNbt() {
        return createNbt();
    }

    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
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
