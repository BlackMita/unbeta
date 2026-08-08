package net.unbeta.content.torch;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.WallTorchBlock;
import net.minecraft.block.HorizontalFacingBlock;
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
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/** Wall-mounted variant of the Unbeta torch. Same lit/unlit + interaction behaviour. */
public class UnbetaWallTorchBlock extends WallTorchBlock implements BlockEntityProvider {

    public static final BooleanProperty LIT = Properties.LIT;

    public UnbetaWallTorchBlock(Settings settings) {
        super(settings, ParticleTypes.FLAME);
        setDefaultState(getDefaultState().with(LIT, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder); // FACING
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

        if (TorchItems.isUnlitTorch(held)) {
            if (isLit) {
                TorchItems.lightHeldTorch(held);
                world.playSound(null, pos, SoundEvents.ITEM_FLINTANDSTEEL_USE, SoundCategory.BLOCKS, 0.6F, 1.3F);
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        }
        // NEW: holding a LIT unbeta torch, click an UNLIT placed torch -> light it. (held-lit-torch lights placed unlit)
        if (!isLit && TorchItems.isTorchItem(held) && TorchItems.isLit(held)) {
            world.setBlockState(pos, state.with(LIT, true), Block.NOTIFY_ALL);
            world.playSound(null, pos, SoundEvents.ITEM_FLINTANDSTEEL_USE, SoundCategory.BLOCKS, 0.6F, 1.1F);
            if (world.getBlockEntity(pos) instanceof UnbetaTorchBlockEntity be) be.onLit(world, pos);
            return ActionResult.SUCCESS;
        }
        if (!isLit && held.isOf(Items.FLINT_AND_STEEL)) {
            world.setBlockState(pos, state.with(LIT, true), Block.NOTIFY_ALL);
            world.playSound(null, pos, SoundEvents.ITEM_FLINTANDSTEEL_USE, SoundCategory.BLOCKS, 0.6F, 1.1F);
            if (world.getBlockEntity(pos) instanceof UnbetaTorchBlockEntity be) be.onLit(world, pos);
            if (!player.getAbilities().creativeMode) held.damage(1, player, p -> p.sendToolBreakStatus(hand));
            return ActionResult.SUCCESS;
        }
        if (isLit && !held.isOf(Items.FLINT_AND_STEEL)) {
            world.setBlockState(pos, state.with(LIT, false), Block.NOTIFY_ALL);
            world.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.4F, 1.4F);
            if (world.getBlockEntity(pos) instanceof UnbetaTorchBlockEntity be) be.onExtinguished();
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }


    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state,
                         @org.jetbrains.annotations.Nullable net.minecraft.entity.LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        boolean lit = TorchItems.isLit(itemStack);
        long ticks = TorchItems.getTicks(itemStack);
        if (world.getBlockEntity(pos) instanceof UnbetaTorchBlockEntity be) {
            be.setRemainingTicks(ticks);
        }
        if (lit && !state.get(LIT)) {
            world.setBlockState(pos, state.with(LIT, true), Block.NOTIFY_ALL);
            if (world.getBlockEntity(pos) instanceof UnbetaTorchBlockEntity be) be.onLit(world, pos);
        }
    }


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
        if (!state.get(LIT)) return;
        Direction dir = state.get(HorizontalFacingBlock.FACING).getOpposite();
        double x = pos.getX() + 0.5 + 0.27 * dir.getOffsetX();
        double y = pos.getY() + 0.7;
        double z = pos.getZ() + 0.5 + 0.27 * dir.getOffsetZ();
        world.addParticle(ParticleTypes.SMOKE, x, y, z, 0, 0, 0);
        world.addParticle(ParticleTypes.FLAME, x, y, z, 0, 0, 0);
    }
}
