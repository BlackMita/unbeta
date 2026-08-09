package net.unbeta.content.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FallingBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

/**
 * Glowsand: a gravity-affected luminous block. Functionally Glowstone that falls like
 * sand. Smelting it produces a Glowstone block. Drops itself when mined (no silk touch
 * needed). Luminance 15 set via Settings.luminance at registration.
 */
public class GlowsandBlock extends FallingBlock {

    public GlowsandBlock(Settings settings) {
        super(settings);
    }

    @Override
    public int getColor(BlockState state, net.minecraft.world.BlockView world, BlockPos pos) {
        return 0xDBBF5A; // sandy yellow
    }
}
