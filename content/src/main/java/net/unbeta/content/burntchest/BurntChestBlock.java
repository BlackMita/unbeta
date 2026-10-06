package net.unbeta.content.burntchest;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * A chest in every way - vanilla's double-chest joining only ever pairs it with another
 * Burnt Chest. Breaking it drops only its contents, unless the tool is gold: a golden
 * pickaxe or axe works like silk touch and brings the chest too.
 */
public class BurntChestBlock extends ChestBlock {

    public BurntChestBlock(Settings settings) {
        super(settings, () -> BurntChests.BLOCK_ENTITY);
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new BurntChestBlockEntity(pos, state);
    }

    @Override
    public void afterBreak(World world, PlayerEntity player, BlockPos pos, BlockState state,
                           @Nullable BlockEntity blockEntity, ItemStack tool) {
        super.afterBreak(world, player, pos, state, blockEntity, tool);
        if (!world.isClient && (tool.isOf(Items.GOLDEN_PICKAXE) || tool.isOf(Items.GOLDEN_AXE))) {
            Block.dropStack(world, pos, new ItemStack(this));
        }
    }
}
