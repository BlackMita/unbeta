package net.unbeta.content.ladder;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.LadderBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.minecraft.world.event.GameEvent;

/**
 * Ladder quality of life.
 *
 * <p><b>Columns.</b> A ladder no longer needs its own wall. It stays up as long as its
 * column (the unbroken run of ladders above and below it) is <i>anchored</i>: some ladder in
 * the column has a wall behind it, or the bottom ladder stands on the solid top of a block.
 * So ladders can stand on a floor, stack up into the air, or hang down past the end of a
 * wall like a rope ladder. Take away the last anchor and the column comes down, rung by rung.
 *
 * <p><b>Stacking.</b> Right-click any ladder while holding a ladder:
 * <ul>
 *   <li>if the space under the column is empty (air), the new ladder goes on the BOTTOM -
 *       so a ladder hung on a wall grows downward until it reaches something;</li>
 *   <li>once the column stands on something, the new ladder goes on TOP.</li>
 * </ul>
 * Sneak-click places ladders the vanilla way.
 */
public final class LadderStacking {

    private static final int MAX_COLUMN = 64;   // how far up or down a column we follow

    private LadderStacking() {}

    public static boolean isLadder(BlockState state) {
        return state.getBlock() instanceof LadderBlock;
    }

    /** Vanilla's own rule: a solid face directly behind the ladder. */
    public static boolean wallBehind(WorldView world, BlockPos pos, Direction facing) {
        BlockPos back = pos.offset(facing.getOpposite());
        return world.getBlockState(back).isSideSolidFullSquare(world, back, facing);
    }

    /**
     * True if a ladder at pos, facing this way, would be held up by its column: a wall behind
     * it or behind any ladder above or below it, or a solid block top under the column.
     */
    public static boolean anchored(WorldView world, BlockPos pos, Direction facing) {
        if (wallBehind(world, pos, facing)) return true;

        BlockPos p = pos;
        for (int i = 0; i < MAX_COLUMN; i++) {           // down the column
            BlockPos down = p.down();
            BlockState s = world.getBlockState(down);
            if (isLadder(s)) {
                if (wallBehind(world, down, s.get(LadderBlock.FACING))) return true;
                p = down;
                continue;
            }
            if (s.isSideSolidFullSquare(world, down, Direction.UP)) return true;
            break;
        }

        p = pos;
        for (int i = 0; i < MAX_COLUMN; i++) {           // up the column
            BlockPos up = p.up();
            BlockState s = world.getBlockState(up);
            if (!isLadder(s)) break;
            if (wallBehind(world, up, s.get(LadderBlock.FACING))) return true;
            p = up;
        }
        return false;
    }

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (player.isSpectator() || player.isSneaking() || !player.canModifyBlocks()) return ActionResult.PASS;
            ItemStack stack = player.getStackInHand(hand);
            if (!(stack.getItem() instanceof BlockItem item) || !(item.getBlock() instanceof LadderBlock)) return ActionResult.PASS;
            BlockPos clicked = hit.getBlockPos();
            if (!isLadder(world.getBlockState(clicked))) return ActionResult.PASS;

            BlockPos bottom = clicked, top = clicked;
            for (int i = 0; i < MAX_COLUMN && isLadder(world.getBlockState(bottom.down())); i++) bottom = bottom.down();
            for (int i = 0; i < MAX_COLUMN && isLadder(world.getBlockState(top.up())); i++) top = top.up();

            // Nothing under the column yet: grow downward. Otherwise grow upward.
            BlockPos target;
            BlockPos from;
            BlockPos under = bottom.down();
            if (world.isInBuildLimit(under) && world.getBlockState(under).isAir()) {
                target = under;
                from = bottom;
            } else {
                target = top.up();
                from = top;
                if (!world.isInBuildLimit(target) || !world.getBlockState(target).isReplaceable()) return ActionResult.PASS;
            }
            if (!world.canPlayerModifyAt(player, target)) return ActionResult.PASS;

            Direction facing = world.getBlockState(from).get(LadderBlock.FACING);
            BlockState place = item.getBlock().getDefaultState().with(LadderBlock.FACING, facing)
                    .with(Properties.WATERLOGGED, world.getFluidState(target).getFluid() == Fluids.WATER);
            if (!place.canPlaceAt(world, target)) return ActionResult.PASS;

            if (!world.isClient) place(player, world, stack, target, place);
            return ActionResult.SUCCESS;   // on the client: swing, and send the click to the server
        });
    }

    private static void place(PlayerEntity player, World world, ItemStack stack, BlockPos at, BlockState state) {
        if (!world.setBlockState(at, state, Block.NOTIFY_ALL)) return;
        BlockSoundGroup sound = state.getSoundGroup();
        world.playSound(null, at, sound.getPlaceSound(), SoundCategory.BLOCKS,
                (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
        world.emitGameEvent(GameEvent.BLOCK_PLACE, at, GameEvent.Emitter.of(player, state));
        if (!player.getAbilities().creativeMode) stack.decrement(1);
    }
}
