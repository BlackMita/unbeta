package net.unbeta.content.gunpowder;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.enums.WireConnection;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import net.minecraft.world.explosion.Explosion;

/**
 * A line of gunpowder laid on the ground. It joins up with its neighbours like redstone dust
 * (also one block up or down), and once lit it burns along and blows up the blocks under it.
 */
public class GunpowderTrailBlock extends Block {

    public static final EnumProperty<WireConnection> NORTH = Properties.NORTH_WIRE_CONNECTION;
    public static final EnumProperty<WireConnection> EAST = Properties.EAST_WIRE_CONNECTION;
    public static final EnumProperty<WireConnection> SOUTH = Properties.SOUTH_WIRE_CONNECTION;
    public static final EnumProperty<WireConnection> WEST = Properties.WEST_WIRE_CONNECTION;
    private static final VoxelShape SHAPE = Block.createCuboidShape(0, 0, 0, 16, 1, 16);

    public GunpowderTrailBlock(AbstractBlock.Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(NORTH, WireConnection.NONE).with(EAST, WireConnection.NONE)
                .with(SOUTH, WireConnection.NONE).with(WEST, WireConnection.NONE));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST);
    }

    private static EnumProperty<WireConnection> prop(Direction d) {
        return switch (d) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            default -> WEST;
        };
    }

    // ------------------------------------------------------------------ shape and joining up

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    public boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        BlockPos below = pos.down();
        return world.getBlockState(below).isSideSolidFullSquare(world, below, Direction.UP);
    }

    public BlockState connected(BlockView world, BlockPos pos) {
        BlockState s = getDefaultState();
        BlockPos above = pos.up();
        boolean openAbove = !world.getBlockState(above).isSolidBlock(world, above);
        for (Direction d : Direction.Type.HORIZONTAL) {
            BlockPos n = pos.offset(d);
            BlockState ns = world.getBlockState(n);
            WireConnection c = WireConnection.NONE;
            if (ns.isOf(this)) c = WireConnection.SIDE;
            else if (openAbove && world.getBlockState(n.up()).isOf(this))
                c = ns.isSideSolidFullSquare(world, n, d.getOpposite()) ? WireConnection.UP : WireConnection.SIDE;
            else if (!ns.isSolidBlock(world, n) && world.getBlockState(n.down()).isOf(this)) c = WireConnection.SIDE;
            s = s.with(prop(d), c);
        }
        return s;
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return connected(ctx.getWorld(), ctx.getBlockPos());
    }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState,
                                                WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.DOWN && !canPlaceAt(state, world, pos)) return Blocks.AIR.getDefaultState();
        return connected(world, pos);
    }

    /** Pieces one step up or down don't hear about each other on their own: tell them. */
    private void refreshDiagonals(World world, BlockPos pos) {
        for (Direction d : Direction.Type.HORIZONTAL) {
            for (int dy = -1; dy <= 1; dy += 2) {
                BlockPos p = pos.offset(d).up(dy);
                BlockState s = world.getBlockState(p);
                if (s.isOf(this)) {
                    BlockState c = connected(world, p);
                    if (c != s) world.setBlockState(p, c, Block.NOTIFY_LISTENERS);
                }
            }
        }
    }

    @Override
    public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        if (oldState.isOf(this)) return;
        refreshDiagonals(world, pos);
        if (!world.isClient) world.scheduleBlockTick(pos, this, 1);
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        super.onStateReplaced(state, world, pos, newState, moved);
        if (!newState.isOf(this)) refreshDiagonals(world, pos);
    }

    // ------------------------------------------------------------------ catching light

    @Override
    public void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        if (!world.isClient) world.scheduleBlockTick(pos, this, 1);
    }

    @Override
    public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (Gunpowder.touchesFlame(world, pos)) Gunpowder.ignite(world, pos);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        ItemStack held = player.getStackInHand(hand);
        if (!Gunpowder.lights(held)) return ActionResult.PASS;
        if (world.isClient) return ActionResult.SUCCESS;
        if (held.isOf(Items.FLINT_AND_STEEL)) {
            world.playSound(null, pos, SoundEvents.ITEM_FLINTANDSTEEL_USE, SoundCategory.BLOCKS, 1F, world.random.nextFloat() * 0.4F + 0.8F);
            held.damage(1, player, p -> p.sendToolBreakStatus(hand));
        } else if (held.isOf(Items.FIRE_CHARGE)) {
            world.playSound(null, pos, SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.BLOCKS, 1F, world.random.nextFloat() * 0.4F + 0.8F);
            if (!player.getAbilities().creativeMode) held.decrement(1);
        }
        Gunpowder.ignite((ServerWorld) world, pos);
        return ActionResult.SUCCESS;
    }

    @Override
    public void onDestroyedByExplosion(World world, BlockPos pos, Explosion explosion) {
        if (world instanceof ServerWorld sw) Gunpowder.ignite(sw, pos);
    }

    @Override
    public ItemStack getPickStack(BlockView world, BlockPos pos, BlockState state) {
        return new ItemStack(Items.GUNPOWDER);
    }
}
