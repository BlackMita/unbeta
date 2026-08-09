package net.unbeta.content.jackolantern;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.CarvedPumpkinBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.state.StateManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import java.util.List;

/** Lit Unbeta Jack o'Lantern. Identical to unlit but defaults to LIT=true. */
public class UnbetaLitJackOLanternBlock extends CarvedPumpkinBlock implements BlockEntityProvider {

    public UnbetaLitJackOLanternBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(JackOLanternLogic.LIT, true));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder); // FACING
        builder.add(JackOLanternLogic.LIT);
    }

    @Nullable @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new JackOLanternBlockEntity(pos, state);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos,
                              PlayerEntity player, Hand hand, BlockHitResult hit) {
        return JackOLanternLogic.onUse(state, world, pos, player, hand);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state,
                         @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        JackOLanternLogic.onPlaced(world, pos, state, itemStack);
    }

    @Override
    public List<ItemStack> getDroppedStacks(BlockState state, LootContextParameterSet.Builder builder) {
        List<ItemStack> drops = super.getDroppedStacks(state, builder);
        JackOLanternLogic.stampDrops(drops, state, builder.getOptional(LootContextParameters.BLOCK_ENTITY));
        return drops;
    }
}
