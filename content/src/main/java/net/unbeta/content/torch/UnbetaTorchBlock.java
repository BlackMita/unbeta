package net.unbeta.content.torch;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.TorchBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.state.StateManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import java.util.List;

public class UnbetaTorchBlock extends TorchBlock implements BlockEntityProvider {

    public static final net.minecraft.state.property.BooleanProperty LIT = TorchLogic.LIT;

    public UnbetaTorchBlock(Settings settings) {
        super(settings, ParticleTypes.FLAME);
        setDefaultState(getDefaultState().with(LIT, false));
    }

    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> b) { b.add(LIT); }

    @Nullable @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new UnbetaTorchBlockEntity(pos, state);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos,
                              PlayerEntity player, Hand hand, BlockHitResult hit) {
        return TorchLogic.onUse(state, world, pos, player, hand);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state,
                         @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        TorchLogic.onPlaced(world, pos, state, itemStack);
    }

    @Override
    public List<ItemStack> getDroppedStacks(BlockState state, LootContextParameterSet.Builder builder) {
        List<ItemStack> drops = super.getDroppedStacks(state, builder);
        TorchLogic.stampDrops(drops, state, builder.getOptional(LootContextParameters.BLOCK_ENTITY));
        return drops;
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (!state.get(LIT)) return;
        double x = pos.getX() + 0.5, y = pos.getY() + 0.7, z = pos.getZ() + 0.5;
        world.addParticle(ParticleTypes.SMOKE, x, y, z, 0, 0, 0);
        world.addParticle(ParticleTypes.FLAME, x, y, z, 0, 0, 0);
    }
}
