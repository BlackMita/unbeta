package net.unbeta.content.mixin;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Implements the Unbeta mining hierarchy:
 *
 * <p>Stone/deepslate reform: if stone or deepslate is broken without the correct tool
 * (iron+), the block immediately replaces itself. Explosions bypass this (handled
 * elsewhere — this mixin only fires on player breaks).
 *
 * <p>Ore → cobblestone: when any ore block is broken, a cobblestone block is placed
 * at that position instead of leaving air. This gives the player a bootstrap path
 * to get cobblestone (needed for furnace) without needing to mine natural stone first.
 */
@Mixin(Block.class)
public class BlockBreakMixin {

    @Inject(method = "afterBreak", at = @At("HEAD"))
    private void unbeta_afterBreak(World world, PlayerEntity player, BlockPos pos,
                                   BlockState state, @Nullable BlockEntity blockEntity,
                                   ItemStack tool, CallbackInfo ci) {
        if (world.isClient) return;

        // --- Stone/deepslate reform ---
        // If the broken block needed iron tool and the tool used wasn't iron+,
        // put the block back. The tag check is the source of truth.
        if (state.isIn(BlockTags.NEEDS_IRON_TOOL)) {
            boolean hasIronPlus = tool.isSuitableFor(state)
                    && !tool.isOf(net.minecraft.item.Items.WOODEN_PICKAXE)
                    && !tool.isOf(net.minecraft.item.Items.STONE_PICKAXE);
            if (!hasIronPlus) {
                world.setBlockState(pos, state, Block.NOTIFY_ALL);
                return;
            }
        }

        // --- Obsidian reform ---
        if (state.isIn(BlockTags.NEEDS_DIAMOND_TOOL)) {
            boolean hasDiamond = tool.isOf(net.minecraft.item.Items.DIAMOND_PICKAXE)
                    || tool.isOf(net.minecraft.item.Items.NETHERITE_PICKAXE);
            if (!hasDiamond) {
                world.setBlockState(pos, state, Block.NOTIFY_ALL);
                return;
            }
        }

        // --- Ore → cobblestone block ---
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
