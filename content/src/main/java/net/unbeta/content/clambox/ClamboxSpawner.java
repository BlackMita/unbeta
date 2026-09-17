package net.unbeta.content.clambox;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.fluid.Fluids;

public final class ClamboxSpawner {

    private static final int TICK_INTERVAL = 400;
    private static final int CONVERT_CHANCE = 80;
    private static final int RADIUS = 48;
    private static final int MIN_SPACING = 32;

    private ClamboxSpawner() {}

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (world.getTime() % TICK_INTERVAL != 0) return;

            for (var player : world.getPlayers()) {
                BlockPos origin = player.getBlockPos();
                for (BlockPos pos : BlockPos.iterateOutwards(origin, RADIUS, RADIUS, RADIUS)) {
                    if (!world.isChunkLoaded(pos)) continue;

                    var state = world.getBlockState(pos);
                    boolean isSeagrass = state.isOf(Blocks.SEAGRASS);
                    boolean isTallBase = state.isOf(Blocks.TALL_SEAGRASS)
                            && state.get(net.minecraft.block.TallSeagrassBlock.HALF)
                               == net.minecraft.block.enums.DoubleBlockHalf.LOWER;
                    if (!isSeagrass && !isTallBase) continue;
                    if (world.getRandom().nextInt(CONVERT_CHANCE) != 0) continue;

                    // Hard stop: don't convert if a clambox already exists within MIN_SPACING
                    if (clamboxNearby(world, pos, MIN_SPACING)) continue;

                    if (isTallBase) {
                        world.setBlockState(pos.up(), Blocks.WATER.getDefaultState());
                    }
                    world.setBlockState(pos, ClamboxRegistry.CLAMBOX_BLOCK.getDefaultState());

                    if (world.getBlockEntity(pos) instanceof ClamboxBlockEntity be) {
                        if (world.getRandom().nextBoolean()) {
                            be.setStack(ClamboxBlockEntity.SLOT_OUTPUT,
                                    ClamboxLoot.roll(world.getRandom()));
                        }
                        be.markDirty();
                    }
                }
            }
        });
    }

    private static boolean clamboxNearby(ServerWorld world, BlockPos origin, int radius) {
        for (BlockPos p : BlockPos.iterateOutwards(origin, radius, 8, radius)) {
            if (!world.isChunkLoaded(p)) continue;
            if (world.getBlockState(p).isOf(ClamboxRegistry.CLAMBOX_BLOCK)) return true;
        }
        return false;
    }
}
