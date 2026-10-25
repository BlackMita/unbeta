package net.unbeta.content.heatediron;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/** Its heat, kept as the world time it will be cold at (so it cools wherever it is). */
public class HeatedIronBlockEntity extends BlockEntity {

    private long heatUntil = -1;

    public HeatedIronBlockEntity(BlockPos pos, BlockState state) {
        super(HeatedIron.BLOCK_ENTITY, pos, state);
    }

    public long heatUntil() {
        return heatUntil;
    }

    public void setHeatUntil(long t) {
        heatUntil = t;
        markDirty();
    }

    public static void serverTick(World world, BlockPos pos, BlockState state, HeatedIronBlockEntity be) {
        long now = world.getTime();
        if (be.heatUntil < 0) be.setHeatUntil(now + HeatedIron.MAX_HEAT);
        boolean water = false, lava = false;
        for (Direction d : Direction.values()) {
            BlockPos n = pos.offset(d);
            if (world.getFluidState(n).isIn(FluidTags.WATER)) water = true;
            else if (world.getFluidState(n).isIn(FluidTags.LAVA)) lava = true;
        }
        if (lava) {
            // Lava heats it back up (twice as fast as it cools), never past 3 minutes' worth.
            be.heatUntil = Math.min(now + HeatedIron.MAX_HEAT, be.heatUntil + 3);
        } else if (water) {
            be.heatUntil -= 3;   // with time's own 1: four times as fast in or beside water
        }
        if (now % 20 == 0) be.markDirty();
        melt(world, pos);
        if (now >= be.heatUntil) {
            world.setBlockState(pos, Blocks.IRON_BLOCK.getDefaultState(), Block.NOTIFY_ALL);
            world.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.5F, 1.4F);
            if (world instanceof ServerWorld sw) sw.spawnParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 6, 0.3, 0.05, 0.3, 0.01);
        }
    }

    /** Snow and ice touching it melt away almost at once (each one after its own few ticks, never into water). */
    private static void melt(World world, BlockPos pos) {
        for (Direction d : Direction.values()) {
            BlockPos n = pos.offset(d);
            BlockState st = world.getBlockState(n);
            boolean snow = st.isOf(Blocks.SNOW) || st.isOf(Blocks.SNOW_BLOCK) || st.isOf(Blocks.POWDER_SNOW);
            if (!snow && !st.isIn(BlockTags.ICE)) continue;
            if (world.random.nextInt(8) != 0) continue;       // about 8 ticks on average, 1 to 25 in practice
            world.setBlockState(n, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            world.playSound(null, n, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.3F, 1.8F);
            if (world instanceof ServerWorld sw) sw.spawnParticles(ParticleTypes.CLOUD, n.getX() + 0.5, n.getY() + 0.5, n.getZ() + 0.5, 3, 0.25, 0.25, 0.25, 0.01);
        }
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putLong(HeatedIron.KEY, heatUntil);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        if (nbt.contains(HeatedIron.KEY)) heatUntil = nbt.getLong(HeatedIron.KEY);
    }
}
