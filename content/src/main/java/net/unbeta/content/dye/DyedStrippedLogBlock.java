package net.unbeta.content.dye;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PillarBlock;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.StringIdentifiable;

/**
 * A painted stripped log. It remembers which wood it was (oak, birch or spruce), so that broken it
 * gives back that log, and with the paint burnt off it is that stripped log again.
 */
public class DyedStrippedLogBlock extends PillarBlock {

    public enum Wood implements StringIdentifiable {
        OAK("oak"), BIRCH("birch"), SPRUCE("spruce");

        private final String name;

        Wood(String name) {
            this.name = name;
        }

        @Override
        public String asString() {
            return name;
        }

        /** Which wood a stripped log (plain or painted) is, or null if it isn't one. */
        public static Wood of(BlockState state) {
            if (state.getBlock() instanceof DyedStrippedLogBlock) return state.get(WOOD);
            if (state.isOf(Blocks.STRIPPED_OAK_LOG)) return OAK;
            if (state.isOf(Blocks.STRIPPED_BIRCH_LOG)) return BIRCH;
            if (state.isOf(Blocks.STRIPPED_SPRUCE_LOG)) return SPRUCE;
            return null;
        }

        /** The plain stripped log of this wood. */
        public Block stripped() {
            return switch (this) {
                case OAK -> Blocks.STRIPPED_OAK_LOG;
                case BIRCH -> Blocks.STRIPPED_BIRCH_LOG;
                case SPRUCE -> Blocks.STRIPPED_SPRUCE_LOG;
            };
        }
    }

    public static final EnumProperty<Wood> WOOD = EnumProperty.of("wood", Wood.class);

    public DyedStrippedLogBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(WOOD, Wood.OAK));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(WOOD);
    }
}
