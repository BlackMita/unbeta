package net.unbeta.content.bucket;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wood and ice bucket pours: puddles of flowing fluid that drain by themselves (never a source).
 * The old timed-source bookkeeping below only cleans up sources left by older versions. No bucket can
 * ever pick one up (TempFluidDrainMixin). All are removed when the world closes, so none
 * can be saved as a permanent source.
 */
public final class TempFluids {

    public static final int WATER_TICKS = 10; // water spreads a block every 5 ticks
    public static final int LAVA_TICKS = 40;  // lava every 30

    private static final Map<RegistryKey<World>, Map<BlockPos, Long>> TEMP = new ConcurrentHashMap<>();

    private TempFluids() {}

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(TempFluids::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (ServerWorld world : server.getWorlds()) {
                Map<BlockPos, Long> m = TEMP.get(world.getRegistryKey());
                if (m != null) m.keySet().forEach(pos -> drain(world, pos));
            }
            TEMP.clear();
        });
    }

    /** Where a pour aimed by this hit would go: the clicked spot if it can hold fluid, else the face in front. */
    public static BlockPos target(World world, BlockHitResult hit) {
        if (hit.getType() != HitResult.Type.BLOCK) return null;
        BlockPos pos = hit.getBlockPos();
        if (canHold(world.getBlockState(pos))) return pos;
        BlockPos side = pos.offset(hit.getSide());
        return canHold(world.getBlockState(side)) ? side : null;
    }

    /** Air, a replaceable plant or snow layer, or flowing fluid - never a source or a solid block. */
    public static boolean canHold(BlockState s) {
        if (s.isAir()) return true;
        if (!s.getFluidState().isEmpty()) return !s.getFluidState().isStill() && s.getBlock() instanceof FluidBlock;
        return s.isReplaceable();
    }

    /**
     * A wood or ice bucket pour: a small puddle of FLOWING fluid - full height where it lands,
     * lower on each open side. With no source behind it, it drains away by itself, and since it
     * is never a source, no number of pours can ever stack into a permanent one.
     */
    public static void place(ServerWorld world, BlockPos pos, Fluid fluid) {
        net.minecraft.fluid.FlowableFluid flowing = fluid == Fluids.LAVA ? Fluids.FLOWING_LAVA : Fluids.FLOWING_WATER;
        splash(world, pos, flowing.getFlowing(7, false).getBlockState());
        for (net.minecraft.util.math.Direction d : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
            BlockPos n = pos.offset(d);
            BlockState at = world.getBlockState(n);
            if (canHold(at) && at.getFluidState().isEmpty()) splash(world, n, flowing.getFlowing(6, false).getBlockState());
        }
    }

    private static void splash(ServerWorld world, BlockPos pos, BlockState state) {
        BlockState at = world.getBlockState(pos);
        if (!at.isAir() && at.getFluidState().isEmpty()) world.breakBlock(pos, true);
        world.setBlockState(pos, state, Block.NOTIFY_ALL);
    }

    public static boolean isTemporary(WorldAccess world, BlockPos pos) {
        if (!(world instanceof World w)) return false;
        Map<BlockPos, Long> m = TEMP.get(w.getRegistryKey());
        return m != null && m.containsKey(pos);
    }

    private static void tick(ServerWorld world) {
        Map<BlockPos, Long> m = TEMP.get(world.getRegistryKey());
        if (m == null || m.isEmpty()) return;
        long now = world.getTime();
        m.entrySet().removeIf(e -> {
            if (e.getValue() > now) return false;
            drain(world, e.getKey());
            return true;
        });
    }

    /** Removes the source if it's still there (lava hit by water may have become stone - leave that). */
    private static void drain(ServerWorld world, BlockPos pos) {
        BlockState s = world.getBlockState(pos);
        if (s.getBlock() instanceof FluidBlock && s.getFluidState().isStill()) {
            world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        }
    }
}
