package net.unbeta.content.mixin;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ore → cobblestone block replacement after a successful break. */
@Mixin(Block.class)
public class BlockBreakMixin {

    @Inject(method = "afterBreak", at = @At("HEAD"))
    private void unbeta_afterBreak(World world, PlayerEntity player, BlockPos pos,
                                   BlockState state, @Nullable BlockEntity blockEntity,
                                   ItemStack tool, CallbackInfo ci) {
        if (world.isClient) return;
        if (state.isIn(BlockTags.COAL_ORES)
                || state.isIn(BlockTags.IRON_ORES)
                || state.isIn(BlockTags.GOLD_ORES)
                || state.isIn(BlockTags.DIAMOND_ORES)
                || state.isIn(BlockTags.REDSTONE_ORES)
                || state.isIn(BlockTags.LAPIS_ORES)
                || state.isIn(BlockTags.EMERALD_ORES)
                || state.isIn(BlockTags.COPPER_ORES)) {
            world.setBlockState(pos, Blocks.COBBLESTONE.getDefaultState(), Block.NOTIFY_ALL);
        }
    }
}
