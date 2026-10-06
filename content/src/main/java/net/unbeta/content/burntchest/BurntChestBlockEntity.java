package net.unbeta.content.burntchest;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public class BurntChestBlockEntity extends ChestBlockEntity {

    public BurntChestBlockEntity(BlockPos pos, BlockState state) {
        super(BurntChests.BLOCK_ENTITY, pos, state);
    }

    @Override
    protected Text getContainerName() {
        return Text.translatable("container.unbeta-content.burnt_chest");
    }
}
