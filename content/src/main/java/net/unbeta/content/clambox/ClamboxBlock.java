package net.unbeta.content.clambox;

import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.FallingBlock;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

/**
 * Clambox: a gravity-affected container found on the sea floor.
 *
 * <p>Falls like sand (extends FallingBlock, same as Glowsand). Pearls its contents only
 * while properly submerged - solid block below, water source on the other five faces -
 * which is checked in the block entity's tick rather than here.
 */
public class ClamboxBlock extends FallingBlock implements BlockEntityProvider {

    public ClamboxBlock(Settings settings) {
        super(settings);
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ClamboxBlockEntity(pos, state);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }
}
