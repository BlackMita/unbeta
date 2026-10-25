package net.unbeta.content.mixin;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.unbeta.content.heatediron.HeatedIron;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A Block of Iron soaks up lava like a sponge soaks up water: when placed, and when lava reaches it. */
@Mixin(AbstractBlock.class)
public abstract class IronBlockAbsorbMixin {

    @Inject(method = "onBlockAdded", at = @At("TAIL"))
    private void unbeta_ironPlaced(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify, CallbackInfo ci) {
        if (state.isOf(Blocks.IRON_BLOCK) && !oldState.isOf(Blocks.IRON_BLOCK)) HeatedIron.tryAbsorb(world, pos);
    }

    @Inject(method = "neighborUpdate", at = @At("TAIL"))
    private void unbeta_lavaArrives(BlockState state, World world, BlockPos pos, Block source, BlockPos sourcePos, boolean notify, CallbackInfo ci) {
        if (state.isOf(Blocks.IRON_BLOCK)) HeatedIron.tryAbsorb(world, pos);
    }
}
