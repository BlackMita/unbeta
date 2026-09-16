package net.unbeta.content.clambox;

import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.FallingBlock;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.random.Random;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.nbt.NbtCompound;

/**
 * Clambox: a gravity-affected container found on the sea floor.
 *
 * <p>Falls like sand (extends FallingBlock, same as Glowsand). Pearls its contents only
 * while properly submerged - solid block below, water source on the other five faces -
 * which is checked in the block entity's tick rather than here.
 */
public class ClamboxBlock extends FallingBlock implements BlockEntityProvider {

    // Positions currently launching as falling blocks - their contents ride along in
    // the falling entity, so onStateReplaced must not scatter them.
    private static final java.util.Set<Long> FALLING =
            java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    public ClamboxBlock(Settings settings) {
        super(settings);
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ClamboxBlockEntity(pos, state);
    }

    @Override
    public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        // Capture the block entity's contents BEFORE the block becomes air.
        NbtCompound beData = null;
        if (world.getBlockEntity(pos) instanceof ClamboxBlockEntity be) {
            beData = be.createNbt();
        }

        // Mark this position so onStateReplaced (fired inside spawnFromBlock) skips scatter.
        FALLING.add(pos.asLong());
        try {
            if (canFallThrough(world.getBlockState(pos.down()))
                    && pos.getY() >= world.getBottomY()) {
                FallingBlockEntity fbe = FallingBlockEntity.spawnFromBlock(world, pos, state);
                if (beData != null) {
                    fbe.blockEntityData = beData; // carried to the landed block
                }
                configureFallingBlockEntity(fbe);
            }
        } finally {
            FALLING.remove(pos.asLong());
        }
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos,
                                BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            // Don't scatter if this removal is the block launching as a falling entity -
            // its contents ride along in the FallingBlockEntity's blockEntityData and are
            // written back on landing. A falling entity for this spot existing right now
            // means "fell", not "destroyed".
            boolean isFalling = FALLING.contains(pos.asLong());
            if (!isFalling && world.getBlockEntity(pos) instanceof ClamboxBlockEntity be) {
                be.scatterItems(world);
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos,
                              PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!world.isClient) {
            if (world.getBlockEntity(pos) instanceof ClamboxBlockEntity be) {
                player.openHandledScreen(be);
            }
        }
        return ActionResult.SUCCESS;
    }
}
