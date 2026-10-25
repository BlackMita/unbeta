package net.unbeta.content.mixin;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.unbeta.content.metal.DiamondRepair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps track of gold wards and diamond blocks' repairs, and slows mining a well-used diamond block. */
@Mixin(AbstractBlock.class)
public abstract class MetalBlocksMixin {

    @Inject(method = "onBlockAdded", at = @At("TAIL"))
    private void unbeta_metalAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify, CallbackInfo ci) {
        if (state.isOf(Blocks.GOLD_BLOCK)) DiamondRepair.changed(world, pos, oldState, state);
    }

    @Inject(method = "onStateReplaced", at = @At("TAIL"))
    private void unbeta_metalReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved, CallbackInfo ci) {
        if (state.isOf(Blocks.GOLD_BLOCK) || state.isOf(Blocks.DIAMOND_BLOCK)) DiamondRepair.changed(world, pos, state, newState);
    }

    @Inject(method = "calcBlockBreakingDelta", at = @At("RETURN"), cancellable = true)
    private void unbeta_wornDiamond(BlockState state, PlayerEntity player, BlockView world, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        if (state.isOf(Blocks.DIAMOND_BLOCK)) cir.setReturnValue(cir.getReturnValueF() / DiamondRepair.slowdown(world, pos));
    }
}
