package net.unbeta.content.heatediron;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/** The glowing orange, cooling iron block. See HeatedIron. */
public class HeatedIronBlock extends Block implements BlockEntityProvider {

    public HeatedIronBlock(Settings settings) {
        super(settings);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new HeatedIronBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient || type != HeatedIron.BLOCK_ENTITY) return null;
        return (w, p, s, be) -> HeatedIronBlockEntity.serverTick(w, p, s, (HeatedIronBlockEntity) be);
    }

    /** Standing on it: half a heart every second (Fire Resistance protects; sneaking doesn't). */
    @Override
    public void onSteppedOn(World world, BlockPos pos, BlockState state, Entity entity) {
        if (!world.isClient && entity instanceof LivingEntity living && living.age % 20 == 0) {
            // hot_floor is fire damage, which Unbeta doubles: 0.5 lands as half a heart.
            living.damage(world.getDamageSources().hotFloor(), 0.5F);
        }
        super.onSteppedOn(world, pos, state, entity);
    }

    /** Under water: bubbles rise from it, and steam puffs where they reach the surface. */
    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        BlockPos up = pos.up();
        if (!world.getFluidState(up).isIn(FluidTags.WATER)) return;
        int top = 1;
        while (top < 24 && world.getFluidState(pos.up(top + 1)).isIn(FluidTags.WATER)) top++;
        for (int i = 0; i < 3; i++) {
            double x = pos.getX() + 0.2 + random.nextDouble() * 0.6, z = pos.getZ() + 0.2 + random.nextDouble() * 0.6;
            double y = pos.getY() + 1.0 + random.nextDouble() * Math.min(top, 3);
            world.addParticle(ParticleTypes.BUBBLE_COLUMN_UP, x, y, z, 0.0, 0.04, 0.0);
        }
        if (world.getBlockState(pos.up(top + 1)).isAir() && random.nextInt(3) == 0) {
            double sx = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6, sz = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6;
            world.addParticle(ParticleTypes.CLOUD, sx, pos.getY() + top + 1.05, sz, 0.0, 0.06, 0.0);
        }
    }
}
