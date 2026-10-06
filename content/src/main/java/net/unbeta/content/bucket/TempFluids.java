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
 * Temporary source blocks from wood buckets. A temporary source lives just long enough for
 * its flow to reach one block beyond it, then vanishes and the flow recedes. No bucket can
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

    public static void place(ServerWorld world, BlockPos pos, Fluid fluid) {
        BlockState at = world.getBlockState(pos);
        if (!at.isAir() && at.getFluidState().isEmpty()) world.breakBlock(pos, true);
        BlockState source = fluid == Fluids.LAVA ? Blocks.LAVA.getDefaultState() : Blocks.WATER.getDefaultState();
        world.setBlockState(pos, source, Block.NOTIFY_ALL);
        TEMP.computeIfAbsent(world.getRegistryKey(), k -> new ConcurrentHashMap<>())
                .put(pos.toImmutable(), world.getTime() + (fluid == Fluids.LAVA ? LAVA_TICKS : WATER_TICKS));
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
