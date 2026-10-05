package net.unbeta.content.worldgen;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RailBlock;
import net.minecraft.block.enums.RailShape;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * A very rare find underground: a straight, flat run of rail on a cave floor with a single
 * minecart sitting on it, as if left behind. Generated with new terrain only - it never
 * appears in chunks that already exist. Rarity is set in the placed feature's JSON.
 */
public class CaveMinecartFeature extends Feature<DefaultFeatureConfig> {

    private static final int MIN_RUN = 6;
    private static final int MAX_RUN = 12;
    /** Must be at least this far below the surface, so it's genuinely a cave. */
    private static final int MIN_DEPTH = 12;

    public CaveMinecartFeature() {
        super(DefaultFeatureConfig.CODEC);
    }

    @Override
    public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
        StructureWorldAccess world = context.getWorld();
        Random random = context.getRandom();
        BlockPos start = findFloor(world, context.getOrigin());
        if (start == null) return false;

        int surface = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, start.getX(), start.getZ());
        if (start.getY() > surface - MIN_DEPTH) return false;

        Direction[] dirs = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        int first = random.nextInt(4);
        int want = MIN_RUN + random.nextInt(MAX_RUN - MIN_RUN + 1);
        for (int k = 0; k < 4; k++) {
            Direction d = dirs[(first + k) % 4];
            int run = 0;
            while (run < want && fits(world, start.offset(d, run))) run++;
            if (run < MIN_RUN) continue;

            RailShape shape = d.getAxis() == Direction.Axis.X ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;
            BlockState rail = Blocks.RAIL.getDefaultState().with(RailBlock.SHAPE, shape);
            for (int i = 0; i < run; i++) {
                world.setBlockState(start.offset(d, i), rail, Block.NOTIFY_LISTENERS);
            }
            BlockPos at = start.offset(d, random.nextInt(run));
            MinecartEntity cart = new MinecartEntity(world.toServerWorld(),
                    at.getX() + 0.5, at.getY() + 0.0625, at.getZ() + 0.5);
            world.spawnEntity(cart);
            return true;
        }
        return false;
    }

    /** The nearest standable cave floor within 12 blocks above or below the origin. */
    private static BlockPos findFloor(StructureWorldAccess world, BlockPos origin) {
        for (int i = 0; i <= 12; i++) {
            if (fits(world, origin.down(i))) return origin.down(i);
            if (i > 0 && fits(world, origin.up(i))) return origin.up(i);
        }
        return null;
    }

    /** Room for a rail and a rider, on a solid floor, out of any water. */
    private static boolean fits(StructureWorldAccess world, BlockPos pos) {
        return world.getBlockState(pos).isAir()
                && world.getBlockState(pos.up()).isAir()
                && world.getFluidState(pos).isEmpty()
                && world.getBlockState(pos.down()).isSolidBlock(world, pos.down());
    }
}
