package net.unbeta.content.obsidiandoor;

import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.BlockSetType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;

/**
 * Obsidian Door.
 *
 * <p>Pass 1: a door that floats (no support block needed), glows at redstone-torch level,
 * and is obsidian-tough. The XP gate comes in pass 2.
 */
public class ObsidianDoorBlock extends DoorBlock {

    public ObsidianDoorBlock(Settings settings, BlockSetType blockSetType) {
        super(settings, blockSetType);
    }

    /**
     * Floating: always placeable, never pops off. Overriding canPlaceAt to always return
     * true stops the vanilla "destroy when block below is gone" logic in
     * getStateForNeighborUpdate, since that path is guarded by !canPlaceAt.
     */
    @Override
    public boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        return true;
    }
}
