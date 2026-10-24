package net.unbeta.content.skeleton;

import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Bone Pile: a falling block with a 9-slot inventory that spawns when a skeleton dies.
 * Fixed break time (~3 seconds regardless of tool). Waterloggable. Lava-immune.
 * Opens a 3x3 UI when right-clicked. Drops its inventory contents when destroyed.
 */
public class BonePileBlock extends FallingBlock implements BlockEntityProvider, Waterloggable {

    public static final BooleanProperty WATERLOGGED = Properties.WATERLOGGED;
    /** True for the ~1s after a skeleton dies: invisible, untargetable, won't fall. */
    public static final BooleanProperty HIDDEN = BooleanProperty.of("hidden");
    private static final VoxelShape SHAPE = Block.createCuboidShape(0, 0, 0, 16, 9, 16);
    /** Positions launching as falling blocks right now: their contents ride along, so no scatter. */
    private static final java.util.Set<Long> FALLING = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    public BonePileBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(WATERLOGGED, false).with(HIDDEN, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(WATERLOGGED, HIDDEN);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext ctx) {
        return state.get(HIDDEN) ? VoxelShapes.empty() : SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext ctx) {
        return VoxelShapes.empty(); // walk through it
    }

    @Override
    public int getColor(BlockState state, BlockView world, BlockPos pos) {
        return 0xDCD2BE; // bone white for falling entity
    }

    // --- Block entity ---

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new BonePileBlockEntity(pos, state);
    }

    // --- Right-click to open inventory ---

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos,
                              PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (state.get(HIDDEN)) return ActionResult.PASS;
        if (!world.isClient) {
            var be = world.getBlockEntity(pos);
            if (be instanceof BonePileBlockEntity bonePile) {
                player.openHandledScreen(bonePile);
            }
        }
        return ActionResult.SUCCESS;
    }

    // --- Drop inventory when destroyed ---

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos,
                                BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && !FALLING.contains(pos.asLong())) {
            var be = world.getBlockEntity(pos);
            if (be instanceof BonePileBlockEntity bonePile) {
                bonePile.scatterItems(world);
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    // --- Waterlogging ---

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        FluidState fluid = ctx.getWorld().getFluidState(ctx.getBlockPos());
        return getDefaultState().with(WATERLOGGED, fluid.getFluid() == Fluids.WATER);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.get(WATERLOGGED) ? Fluids.WATER.getStill(false) : super.getFluidState(state);
    }

    // --- Delayed reveal ---

    /**
     * While HIDDEN, this tick is the reveal timer rather than the falling check.
     * FallingBlock also schedules ticks (every placement/neighbour change), so an early
     * tick just re-schedules for the remaining wait. Once revealed, we hand back to
     * FallingBlock's normal behaviour and schedule a fall check in case it's floating.
     */
    @Override
    public void scheduledTick(BlockState state, net.minecraft.server.world.ServerWorld world,
                              BlockPos pos, net.minecraft.util.math.random.Random random) {
        if (state.get(HIDDEN)) {
            if (world.getBlockEntity(pos) instanceof BonePileBlockEntity be) {
                long wait = be.getRevealAt() - world.getTime();
                if (wait > 0) {
                    world.scheduleBlockTick(pos, this, (int) wait);
                    return;
                }
            }
            world.setBlockState(pos, state.with(HIDDEN, false), Block.NOTIFY_ALL);
            world.scheduleBlockTick(pos, this, this.getFallDelay());
            return;
        }
        fall(state, world, pos);
    }

    /**
     * Falls like a clambox: the contents (and respawn time) ride along in the falling block and
     * are written back where it lands, instead of spilling out where it started.
     */
    private void fall(BlockState state, net.minecraft.server.world.ServerWorld world, BlockPos pos) {
        if (!canFallThrough(world.getBlockState(pos.down())) || pos.getY() < world.getBottomY()) return;
        net.minecraft.nbt.NbtCompound data = world.getBlockEntity(pos) instanceof BonePileBlockEntity be ? be.createNbt() : null;
        FALLING.add(pos.asLong());
        try {
            net.minecraft.entity.FallingBlockEntity fbe = net.minecraft.entity.FallingBlockEntity.spawnFromBlock(world, pos, state.with(WATERLOGGED, false));
            if (data != null) fbe.blockEntityData = data;
            configureFallingBlockEntity(fbe);
        } finally {
            FALLING.remove(pos.asLong());
        }
    }

    /** Landed: the skeleton still comes back on time, from here now. */
    @Override
    public void onLanding(World world, BlockPos pos, BlockState fallingState, BlockState current,
                          net.minecraft.entity.FallingBlockEntity fbe) {
        if (!(world instanceof net.minecraft.server.world.ServerWorld sw) || fbe.blockEntityData == null) return;
        if (!fbe.blockEntityData.contains("RespawnAt")) return;
        long at = fbe.blockEntityData.getLong("RespawnAt");
        if (at < 0) return;
        long left = Math.max(1, at - sw.getTime());
        long warn = left - (BonePileRespawn.RESPAWN_TICKS - BonePileRespawn.WARNING_TICKS);
        if (warn > 0) net.unbeta.core.sched.UnbetaScheduler.schedule(sw, pos, warn, BonePileRespawn.WARNING_HANDLER_ID);
        net.unbeta.core.sched.UnbetaScheduler.schedule(sw, pos, left, BonePileRespawn.RESPAWN_HANDLER_ID);
    }

    /** Broke on landing (fell onto a torch, a slab...): its contents spill there. */
    @Override
    public void onDestroyedOnLanding(World world, BlockPos pos, net.minecraft.entity.FallingBlockEntity fbe) {
        if (world.isClient || fbe.blockEntityData == null) return;
        net.minecraft.util.collection.DefaultedList<net.minecraft.item.ItemStack> items =
                net.minecraft.util.collection.DefaultedList.ofSize(9, net.minecraft.item.ItemStack.EMPTY);
        net.minecraft.inventory.Inventories.readNbt(fbe.blockEntityData, items);
        for (net.minecraft.item.ItemStack st : items) if (!st.isEmpty()) Block.dropStack(world, pos, st);
    }

    // --- Render type ---

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return state.get(HIDDEN) ? BlockRenderType.INVISIBLE : BlockRenderType.MODEL;
    }

    @Override
    public void neighborUpdate(BlockState state, net.minecraft.world.World world, BlockPos pos,
                               net.minecraft.block.Block sourceBlock, BlockPos sourcePos, boolean notify) {
        super.neighborUpdate(state, world, pos, sourceBlock, sourcePos, notify);
        if (world.isClient) return;
        // Source water or lava adjacent destroys the bone pile
        net.minecraft.fluid.FluidState fluid = world.getFluidState(pos);
        if (!fluid.isEmpty() && fluid.isStill()) {
            var be = world.getBlockEntity(pos);
            if (be instanceof BonePileBlockEntity bonePile) {
                bonePile.scatterItems(world);
                bonePile.clear();
            }
            world.breakBlock(pos, true); // runs the loot table (1-2 bones), same as breaking by hand
        }
    }

}