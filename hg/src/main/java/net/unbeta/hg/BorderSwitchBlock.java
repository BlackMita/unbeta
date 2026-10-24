package net.unbeta.hg;

import net.minecraft.block.BlockState;
import net.minecraft.block.LeverBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * The border switch on the diamond block in the middle of the cage: a lever dressed in diamond.
 * Flipping it decides which way the world border crawls (see Border). It can't be broken, and it
 * only flips for a player still in the round.
 */
public class BorderSwitchBlock extends LeverBlock {

    public BorderSwitchBlock(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;
        if (UnbetaHG.ACTIVE && player instanceof ServerPlayerEntity sp && world instanceof ServerWorld sw) {
            Border.flip(sp, sw, pos, state);
        }
        return ActionResult.SUCCESS;
    }
}
