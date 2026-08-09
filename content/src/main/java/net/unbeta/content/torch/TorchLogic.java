package net.unbeta.content.torch;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * THE single implementation for all Unbeta torch behaviour.
 * Both floor and wall torches delegate here - they can never drift apart.
 */
public final class TorchLogic {

    public static final BooleanProperty LIT = Properties.LIT;

    private TorchLogic() {}

    // ---------------------------------------------------- placed torch

    public static void lightPlaced(World world, BlockPos pos, BlockState state) {
        if (state.get(LIT)) return;
        world.setBlockState(pos, state.with(LIT, true), Block.NOTIFY_ALL);
        if (world.getBlockEntity(pos) instanceof UnbetaTorchBlockEntity be) {
            be.light(world, pos);
        }
        world.playSound(null, pos, SoundEvents.ITEM_FLINTANDSTEEL_USE, SoundCategory.BLOCKS, 0.6F, 1.1F);
    }

    public static void extinguishPlaced(World world, BlockPos pos, BlockState state) {
        if (!state.get(LIT)) return;
        world.setBlockState(pos, state.with(LIT, false), Block.NOTIFY_ALL);
        if (world.getBlockEntity(pos) instanceof UnbetaTorchBlockEntity be) {
            be.extinguish();
        }
        world.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.4F, 1.4F);
    }

    // ---------------------------------------------------- right-click matrix

    /**
     * Use matrix (same for floor and wall):
     *
     * held=UNLIT torch + placed LIT  → swap held to lit (placed untouched)
     * held=LIT torch   + placed UNLIT → light the placed torch (held untouched)
     * held=flint&steel + placed UNLIT → light the placed torch
     * held=anything    + placed LIT   → extinguish placed
     */
    public static ActionResult onUse(BlockState state, World world, BlockPos pos,
                                     PlayerEntity player, Hand hand) {
        ItemStack held = player.getStackInHand(hand);
        boolean placedLit = state.get(LIT);
        long now = world.getTime();

        // 1. Held UNLIT torch + placed LIT → light the HELD one (item swap)
        if (TorchItems.isUnlitTorch(held)) {
            if (placedLit) {
                if (!world.isClient) {
                    player.setStackInHand(hand, TorchItems.createLit(held, now));
                }
                world.playSound(null, pos, SoundEvents.ITEM_FLINTANDSTEEL_USE,
                        SoundCategory.BLOCKS, 0.6F, 1.3F);
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        }

        // 2. Igniter + placed UNLIT → light the placed torch
        boolean igniter = held.isOf(Items.FLINT_AND_STEEL) || TorchItems.isLitTorch(held);
        if (!placedLit && igniter) {
            if (!world.isClient) {
                lightPlaced(world, pos, state);
                if (held.isOf(Items.FLINT_AND_STEEL) && !player.getAbilities().creativeMode) {
                    held.damage(1, player, p -> p.sendToolBreakStatus(hand));
                }
            }
            return ActionResult.SUCCESS;
        }

        // 3. Placed LIT + non-igniter → extinguish
        if (placedLit && !igniter) {
            if (!world.isClient) extinguishPlaced(world, pos, state);
            return ActionResult.SUCCESS;
        }

        return ActionResult.PASS;
    }

    // ---------------------------------------------------- place / mine

    /** Place: adopt item's state verbatim, no re-anchoring. */
    public static void onPlaced(World world, BlockPos pos, BlockState state, ItemStack itemStack) {
        boolean lit = TorchItems.isLitTorch(itemStack);
        long burnoutAt = TorchItems.getBurnoutAt(itemStack);
        long full = TorchItems.getFull(itemStack);

        if (world.getBlockEntity(pos) instanceof UnbetaTorchBlockEntity be) {
            be.adopt(lit, burnoutAt, full);
        }
        if (lit && !state.get(LIT)) {
            world.setBlockState(pos, state.with(LIT, true), Block.NOTIFY_ALL);
            if (world instanceof ServerWorld sw
                    && world.getBlockEntity(pos) instanceof UnbetaTorchBlockEntity be2) {
                long remaining = be2.getRemainingTicks();
                if (remaining > 0) {
                    net.unbeta.core.sched.UnbetaScheduler.schedule(
                            sw, pos, remaining, TorchBurnout.HANDLER_ID);
                }
            }
        }
    }

    /** Mine: replace loot-table drop with the correct item for the current state. */
    public static void stampDrops(List<ItemStack> drops, BlockState state, BlockEntity be) {
        boolean lit = state.get(LIT);
        for (int i = 0; i < drops.size(); i++) {
            ItemStack drop = drops.get(i);
            if (!drop.isOf(UnbetaTorchRegistry.TORCH_ITEM)) continue;
            if (lit && be instanceof UnbetaTorchBlockEntity tbe) {
                drops.set(i, TorchItems.createLitFromBlock(tbe.getBurnoutAt(), tbe.getFull()));
            }
            // unlit → loot table already produced TORCH_ITEM, nothing to do
        }
    }
}
