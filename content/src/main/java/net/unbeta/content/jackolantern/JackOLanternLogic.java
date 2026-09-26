package net.unbeta.content.jackolantern;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.unbeta.content.torch.TorchItems;

import java.util.List;

/**
 * Shared logic for Unbeta Jack o'Lanterns - mirrors TorchLogic exactly.
 * Two blocks (unlit/lit) delegate everything here so they can never drift.
 */
public final class JackOLanternLogic {

    public static final BooleanProperty LIT = Properties.LIT;

    /** Burn duration: 1 full Minecraft day+night = 24000 ticks. */
    public static final long FULL_BURN_TICKS = 24000L; // 1 full Minecraft day

    private JackOLanternLogic() {}

    public static void lightPlaced(World world, BlockPos pos, BlockState state) {
        if (state.get(LIT)) return;
        // Cannot relight underwater - check if water is at or above this position
        if (!world.getFluidState(pos).isEmpty()
                || !world.getFluidState(pos.up()).isEmpty()) return;
        world.setBlockState(pos, state.with(LIT, true), Block.NOTIFY_ALL);
        if (world.getBlockEntity(pos) instanceof JackOLanternBlockEntity be) {
            be.light(world, pos);
        }
        world.playSound(null, pos, SoundEvents.ITEM_FLINTANDSTEEL_USE,
                SoundCategory.BLOCKS, 0.6F, 1.1F);
    }

    public static void extinguishPlaced(World world, BlockPos pos, BlockState state) {
        if (!state.get(LIT)) return;
        world.setBlockState(pos, state.with(LIT, false), Block.NOTIFY_ALL);
        if (world.getBlockEntity(pos) instanceof JackOLanternBlockEntity be) {
            be.extinguish();
        }
        world.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH,
                SoundCategory.BLOCKS, 0.4F, 1.4F);
    }

    /** Right-click rules: shared with every other placed light - see HeldLight. */
    public static ActionResult onUse(BlockState state, World world, BlockPos pos,
                                     PlayerEntity player, Hand hand) {
        return net.unbeta.content.light.HeldLight.onUse(state.get(LIT), world, pos, player, hand,
                () -> lightPlaced(world, pos, state),
                () -> extinguishPlaced(world, pos, state));
    }

    public static void onPlaced(World world, BlockPos pos, BlockState state, ItemStack itemStack) {
        boolean lit = JackOLanternItems.isLit(itemStack);
        long burnoutAt = JackOLanternItems.getBurnoutAt(itemStack);
        long full = JackOLanternItems.getFull(itemStack);
        if (world.getBlockEntity(pos) instanceof JackOLanternBlockEntity be) {
            be.adopt(lit, burnoutAt, full);
        }
        if (lit && !state.get(LIT)) {
            world.setBlockState(pos, state.with(LIT, true), Block.NOTIFY_ALL);
            if (world instanceof ServerWorld sw
                    && world.getBlockEntity(pos) instanceof JackOLanternBlockEntity be2) {
                long remaining = be2.getRemainingTicks();
                if (remaining > 0)
                    net.unbeta.core.sched.UnbetaScheduler.schedule(
                            sw, pos, remaining, JackOLanternBurnout.HANDLER_ID);
            }
        }
    }

    public static void stampDrops(List<ItemStack> drops, BlockState state, BlockEntity be) {
        boolean lit = state.get(LIT);
        for (int i = 0; i < drops.size(); i++) {
            ItemStack drop = drops.get(i);
            if (!drop.isOf(JackOLanternRegistry.UNLIT_ITEM)) continue;
            if (lit && be instanceof JackOLanternBlockEntity tbe) {
                drops.set(i, JackOLanternItems.createLitFromBlock(
                        tbe.getBurnoutAt(), tbe.getFull()));
            }
        }
    }
}
