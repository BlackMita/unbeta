package net.unbeta.content.clambox;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Converts seagrass into clamboxes with a small probability.
 *
 * <p>Seagrass is already guaranteed to sit on a solid ocean floor block with water
 * above it - exactly the clambox's working conditions. No placement math needed.
 *
 * <p>Every 20 seconds, for each player, scan a 32-block radius around them and
 * give each seagrass block a 1-in-400 chance to become a clambox. Tall seagrass
 * is replaced at its base (bottom half) only.
 */
public final class ClamboxSpawner {

    private static final int TICK_INTERVAL = 400; // 20 seconds
    private static final int CONVERT_CHANCE = 80; // 1 in 400 per seagrass per pass
    private static final int RADIUS = 48;

    private ClamboxSpawner() {}

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (world.getTime() % TICK_INTERVAL != 0) return;

            for (var player : world.getPlayers()) {
                BlockPos origin = player.getBlockPos();
                for (BlockPos pos : BlockPos.iterateOutwards(origin, RADIUS, RADIUS, RADIUS)) {
                    if (!world.isChunkLoaded(pos)) continue;
                    if (world.getRandom().nextInt(CONVERT_CHANCE) != 0) continue;

                    var state = world.getBlockState(pos);
                    boolean isSeagrass = state.isOf(Blocks.SEAGRASS);
                    boolean isTallBase = state.isOf(Blocks.TALL_SEAGRASS)
                            && state.get(net.minecraft.block.TallSeagrassBlock.HALF)
                               == net.minecraft.block.enums.DoubleBlockHalf.LOWER;

                    if (!isSeagrass && !isTallBase) continue;

                    // Replace with clambox
                    if (isTallBase) {
                        // Remove the top half first
                        world.setBlockState(pos.up(), Blocks.WATER.getDefaultState());
                    }
                    world.setBlockState(pos,
                            ClamboxRegistry.CLAMBOX_BLOCK.getDefaultState());

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
}
