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
 * Ladder quality of life:
 * <ul>
 *   <li>Right-click any ladder while holding a ladder: one is added on TOP of that ladder's
 *       column (if there is room), facing the same way - like stacking scaffolding.</li>
 *   <li>Ladders can stand on the top face of a block or on another ladder (see LadderSupportMixin),
 *       so clicking a floor puts a ladder on it, facing you.</li>
 * </ul>
 * Sneak-click to place a ladder the vanilla way instead.
 */
public final class LadderStacking {

    private static final int MAX_CLIMB = 64;   // how far up a column we look for its top

    private LadderStacking() {}

    /** True if a ladder at pos is held up from beneath: a ladder, or the solid top of a block. */
    public static boolean supportedFromBelow(WorldView world, BlockPos pos) {
        BlockPos down = pos.down();
        BlockState below = world.getBlockState(down);
        return below.getBlock() instanceof LadderBlock || below.isSideSolidFullSquare(world, down, Direction.UP);
    }

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (player.isSpectator() || player.isSneaking() || !player.canModifyBlocks()) return ActionResult.PASS;
            ItemStack stack = player.getStackInHand(hand);
            if (!(stack.getItem() instanceof BlockItem item) || !(item.getBlock() instanceof LadderBlock)) return ActionResult.PASS;
            BlockPos clicked = hit.getBlockPos();
            if (!(world.getBlockState(clicked).getBlock() instanceof LadderBlock)) return ActionResult.PASS;

            // Climb to the top of the column.
            BlockPos top = clicked;
            for (int i = 0; i < MAX_CLIMB && world.getBlockState(top.up()).getBlock() instanceof LadderBlock; i++) top = top.up();
            BlockPos target = top.up();
            if (!world.isInBuildLimit(target) || !world.getBlockState(target).isReplaceable()
                    || !world.canPlayerModifyAt(player, target)) return ActionResult.PASS;

            Block block = item.getBlock();
            Direction facing = world.getBlockState(top).get(LadderBlock.FACING);
            BlockState place = block.getDefaultState().with(LadderBlock.FACING, facing)
                    .with(Properties.WATERLOGGED, world.getFluidState(target).getFluid() == Fluids.WATER);
            if (!place.canPlaceAt(world, target)) return ActionResult.PASS;

            if (!world.isClient) place(player, world, hand, stack, target, place);
            return ActionResult.SUCCESS;   // on the client: swing, and send the click to the server
        });
    }

    private static void place(PlayerEntity player, World world, Hand hand, ItemStack stack, BlockPos at, BlockState state) {
        if (!world.setBlockState(at, state, Block.NOTIFY_ALL)) return;
        BlockSoundGroup sound = state.getSoundGroup();
        world.playSound(null, at, sound.getPlaceSound(), SoundCategory.BLOCKS,
                (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
        world.emitGameEvent(GameEvent.BLOCK_PLACE, at, GameEvent.Emitter.of(player, state));
        if (!player.getAbilities().creativeMode) stack.decrement(1);
    }
}
