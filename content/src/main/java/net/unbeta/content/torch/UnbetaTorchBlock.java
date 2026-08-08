package net.unbeta.content.torch;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.TorchBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The Unbeta standing torch. Has a LIT state (like a redstone torch). When lit it burns
 * down over time and reverts to unlit (handled by the block entity + scheduler).
 *
 * <p>Right-click matrix (pass 1):
 * <ul>
 *   <li>UNLIT + flint&steel → lights.</li>
 *   <li>any state + holding an UNLIT unbeta torch → lights the HELD torch (placed one
 *       unaffected).</li>
 *   <li>LIT + anything else (not flint&steel, not a held unlit torch) → extinguishes.</li>
 * </ul>
 * Left-click / mining never extinguishes (vanilla break; the dropped item keeps its state).
 */
public class UnbetaTorchBlock extends TorchBlock implements BlockEntityProvider {

    public static final BooleanProperty LIT = Properties.LIT;

    public UnbetaTorchBlock(Settings settings) {
        super(settings, ParticleTypes.FLAME);
        setDefaultState(getDefaultState().with(LIT, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new UnbetaTorchBlockEntity(pos, state);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos,
                              PlayerEntity player, Hand hand, BlockHitResult hit) {
        ItemStack held = player.getStackInHand(hand);
        boolean isLit = state.get(LIT);

        // Case 1: holding an UNLIT unbeta torch -> light the HELD torch from this one.
        // (Works whether this placed torch is lit or not? Spec: torch-to-torch needs a LIT
        //  source. So only if THIS one is lit.)
        if (TorchItems.isUnlitTorch(held)) {
            if (isLit) {
                TorchItems.lightHeldTorch(held);
                world.playSound(null, pos, SoundEvents.ITEM_FLINTANDSTEEL_USE,
                        SoundCategory.BLOCKS, 0.6F, 1.3F);
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS; // both unlit: nothing to light from
        }

        // NEW: holding a LIT unbeta torch, click an UNLIT placed torch -> light it (like flint&steel).
        // (held-lit-torch lights placed unlit)
        if (!isLit && TorchItems.isTorchItem(held) && TorchItems.isLit(held)) {
            world.setBlockState(pos, state.with(LIT, true), Block.NOTIFY_ALL);
            world.playSound(null, pos, SoundEvents.ITEM_FLINTANDSTEEL_USE, SoundCategory.BLOCKS, 0.6F, 1.1F);
            if (world.getBlockEntity(pos) instanceof UnbetaTorchBlockEntity be) be.onLit(world, pos);
            return ActionResult.SUCCESS;
        }

        // Case 2: UNLIT placed torch + flint & steel -> light it.
        if (!isLit && held.isOf(Items.FLINT_AND_STEEL)) {
            light(world, pos, state);
            if (!player.getAbilities().creativeMode) {
                held.damage(1, player, p -> p.sendToolBreakStatus(hand));
            }
            return ActionResult.SUCCESS;
        }

        // Case 3: LIT placed torch + anything else (not flint&steel) -> extinguish.
        if (isLit && !held.isOf(Items.FLINT_AND_STEEL)) {
            extinguish(world, pos, state);
            return ActionResult.SUCCESS;
        }

        return ActionResult.PASS;
    }

    /** Light this placed torch and start its burn timer via the block entity. */
    public static void light(World world, BlockPos pos, BlockState state) {
        world.setBlockState(pos, state.with(LIT, true), Block.NOTIFY_ALL);
        world.playSound(null, pos, SoundEvents.ITEM_FLINTANDSTEEL_USE,
                SoundCategory.BLOCKS, 0.6F, 1.1F);
        if (world.getBlockEntity(pos) instanceof UnbetaTorchBlockEntity be) {
            be.onLit(world, pos);
        }
    }

    public static void extinguish(World world, BlockPos pos, BlockState state) {
        world.setBlockState(pos, state.with(LIT, false), Block.NOTIFY_ALL);
        world.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH,
                SoundCategory.BLOCKS, 0.4F, 1.4F);
        if (world.getBlockEntity(pos) instanceof UnbetaTorchBlockEntity be) {
            be.onExtinguished();
        }
    }


    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state,
                         @Nullable net.minecraft.entity.LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        boolean lit = TorchItems.isLit(itemStack);
        long ticks = TorchItems.getTicks(itemStack);
        if (world.getBlockEntity(pos) instanceof UnbetaTorchBlockEntity be) {
            be.setRemainingTicks(ticks);
        }
        if (lit && !state.get(LIT)) {
            // Set lit quietly - no ignition sound on placement (the torch was already lit).
            world.setBlockState(pos, state.with(LIT, true), Block.NOTIFY_ALL);
            if (world.getBlockEntity(pos) instanceof UnbetaTorchBlockEntity be2) {
                be2.onLit(world, pos);
            }
        }
    }


    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos,
                                BlockState newState, boolean moved) {
        // (drop handling done via loot table + we stamp the dropped stack in onBreak path)
        super.onStateReplaced(state, world, pos, newState, moved);
    }


    // FIX 3 (clean): the dropped item carries the torch's lit state + remaining burn-life.
    @Override
    public java.util.List<ItemStack> getDroppedStacks(BlockState state, net.minecraft.loot.context.LootContextParameterSet.Builder builder) {
        java.util.List<ItemStack> drops = super.getDroppedStacks(state, builder);
        if (state.get(LIT)) {
            net.minecraft.block.entity.BlockEntity be =
                    builder.getOptional(net.minecraft.loot.context.LootContextParameters.BLOCK_ENTITY);
            long ticks = (be instanceof UnbetaTorchBlockEntity tbe)
                    ? tbe.getRemainingTicks() : UnbetaTorchBlockEntity.DEFAULT_BURN_TICKS;
            for (ItemStack drop : drops) {
                if (drop.isOf(UnbetaTorchRegistry.TORCH_ITEM)) {
                    TorchItems.lightHeldTorch(drop);
                    drop.getOrCreateNbt().putLong(TorchItems.NBT_TICKS, ticks);
                }
            }
        }
        return drops;
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (!state.get(LIT)) return; // unlit: no flame/smoke
        double x = pos.getX() + 0.5, y = pos.getY() + 0.7, z = pos.getZ() + 0.5;
        world.addParticle(ParticleTypes.SMOKE, x, y, z, 0, 0, 0);
        world.addParticle(ParticleTypes.FLAME, x, y, z, 0, 0, 0);
    }
}
