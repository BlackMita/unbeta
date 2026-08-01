package net.unbeta.content.block;

import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

/**
 * Obsidian-exclusive coloured fire. Modelled on vanilla SoulFireBlock, which is itself
 * a second AbstractFireBlock chosen by AbstractFireBlock.getState() based on the block
 * below. We add a third such block, placed over obsidian, carrying a random COLOR.
 *
 * <p>Behaviour: identical to regular fire for damage and light (AbstractFireBlock does
 * that). Differences: it is PERMANENT - it has no AGE property and never schedules a
 * tick to extinguish or spread, so time and rain never remove it. Only water or the
 * player breaking it removes it (both are handled by the block simply being destroyed).
 *
 * <p>Damage value is 1.0 like vanilla fire (AbstractFireBlock passes a damage source);
 * SoulFireBlock overrides to 2.0. We keep 1.0 to match "behaves like regular fire".
 */
public class ObsidianFireBlock extends AbstractFireBlock {

    public static final EnumProperty<FireColor> COLOR = EnumProperty.of("color", FireColor.class);

    public ObsidianFireBlock(Settings settings) {
        super(settings, 1.0F); // 1.0 damage, same as regular fire
        setDefaultState(getDefaultState().with(COLOR, FireColor.BLUE));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(COLOR);
    }

    /**
     * Fire can exist here if the block below is obsidian and the position is valid.
     * AbstractFireBlock#canPlaceAt already checks the side-solid condition; we add the
     * obsidian requirement.
     */
    @Override
    public boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        return isObsidianBelow(world, pos) && world.getBlockState(pos.down()).isSideSolidFullSquare(world, pos.down(), net.minecraft.util.math.Direction.UP);
    }

    public static boolean isObsidianBelow(BlockView world, BlockPos pos) {
        return world.getBlockState(pos.down()).isOf(Blocks.OBSIDIAN);
    }

    /** A fresh state with a random colour - used at ignition time. */
    public BlockState withRandomColor(Random random) {
        return getDefaultState().with(COLOR, FireColor.VALUES[random.nextInt(FireColor.VALUES.length)]);
    }

    // No scheduledTick / randomTick override: AbstractFireBlock's base does not spread
    // or extinguish on its own (that logic lives in the concrete FireBlock subclass,
    // which we intentionally do not extend). So this fire is permanent by construction.

    /**
     * Obsidian fire is silent and smokeless. Vanilla AbstractFireBlock#randomDisplayTick
     * plays BLOCK_FIRE_AMBIENT and spawns LARGE_SMOKE particles; we override it to do
     * nothing, giving these mystical colored flames a clean, quiet look. Client-only
     * cosmetic - no gameplay effect.
     */
    @Override
    public void randomDisplayTick(net.minecraft.block.BlockState state,
                                  net.minecraft.world.World world,
                                  net.minecraft.util.math.BlockPos pos,
                                  net.minecraft.util.math.random.Random random) {
        // intentionally empty: no ambient crackle, no smoke particles
    }

    @Override
    protected boolean isFlammable(net.minecraft.block.BlockState state) {
        return false;
    }
}
