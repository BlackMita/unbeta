package net.unbeta.content.rails;

import com.mojang.serialization.Codec;
import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.RailBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.WallTorchBlock;
import net.minecraft.block.enums.RailShape;
import net.minecraft.block.enums.SlabType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * Generates this chunk's share of the overworld rail network.
 *
 * SLOPE MODEL: when two nodes are on different Y tiers, the elevation change is
 * concentrated into one continuous ramp section near the midpoint of the path, not
 * distributed as tiny 1-block steps across the whole length. Same-tier connections
 * are perfectly flat — zero Y changes anywhere along the run.
 *
 * PRIORITY: rails and planks are never overwritten by fence posts. When two segments
 * cross, the first one's structural blocks survive.
 */
public class RailSegmentFeature extends Feature<DefaultFeatureConfig> {

    private static final int GAP_CHANCE = 3;
    private static final int TORCH_CHANCE = 4;
    private static final int HEADROOM = 3;

    public RailSegmentFeature(Codec<DefaultFeatureConfig> codec) {
        super(codec);
    }

    @Override
    public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
        StructureWorldAccess world = context.getWorld();
        BlockPos origin = context.getOrigin();
        long seed = world.getSeed();

        int minX = (origin.getX() >> 4) << 4;
        int minZ = (origin.getZ() >> 4) << 4;
        int maxX = minX + 15;
        int maxZ = minZ + 15;

        int ccx = RailNetwork.cellOf(minX);
        int ccz = RailNetwork.cellOf(minZ);

        boolean any = false;
        for (int cx = ccx - 1; cx <= ccx + 1; cx++) {
            for (int cz = ccz - 1; cz <= ccz + 1; cz++) {
                any |= trySegment(world, seed, cx, cz, RailNetwork.DIR_EAST,
                        cx + 1, cz, minX, minZ, maxX, maxZ);
                any |= trySegment(world, seed, cx, cz, RailNetwork.DIR_SOUTH,
                        cx, cz + 1, minX, minZ, maxX, maxZ);
                // Deep network — same drawing, deeper nodes
                any |= tryDeepSegment(world, seed, cx, cz, RailNetwork.DEEP_DIR_EAST,
                        cx + 1, cz, minX, minZ, maxX, maxZ);
                any |= tryDeepSegment(world, seed, cx, cz, RailNetwork.DEEP_DIR_SOUTH,
                        cx, cz + 1, minX, minZ, maxX, maxZ);
            }
        }
        return any;
    }

    private boolean tryDeepSegment(StructureWorldAccess world, long seed,
                                   int cx, int cz, int dir, int ncx, int ncz,
                                   int minX, int minZ, int maxX, int maxZ) {
        if (!RailNetwork.deepHasConnection(seed, cx, cz, dir)) return false;
        int x1 = RailNetwork.deepNodeX(seed, cx, cz);
        int z1 = RailNetwork.deepNodeZ(seed, cx, cz);
        int y1 = RailNetwork.deepNodeY(seed, cx, cz);
        int x2 = RailNetwork.deepNodeX(seed, ncx, ncz);
        int z2 = RailNetwork.deepNodeZ(seed, ncx, ncz);
        int y2 = RailNetwork.deepNodeY(seed, ncx, ncz);

        int pad = 3;
        if (Math.max(x1, x2) + pad < minX || Math.min(x1, x2) - pad > maxX) return false;
        if (Math.max(z1, z2) + pad < minZ || Math.min(z1, z2) - pad > maxZ) return false;

        long segSeed = RailNetwork.deepSegmentSeed(seed, cx, cz, dir);
        boolean xFirst = (dir == RailNetwork.DEEP_DIR_EAST);
        int[][] path = buildLPath(x1, z1, x2, z2, xFirst);
        if (path.length < 2) return false;

        int lastIdx = path.length - 1;
        int yDiff = y2 - y1;
        int absYDiff = Math.abs(yDiff);
        int rampStart = (lastIdx - absYDiff) / 2;
        int rampEnd = rampStart + absYDiff;
        if (rampStart < 0) rampStart = 0;
        if (rampEnd > lastIdx) rampEnd = lastIdx;

        boolean placed = false;
        for (int i = 0; i < path.length; i++) {
            int x = path[i][0];
            int z = path[i][1];
            if (x < minX || x > maxX || z < minZ || z > maxZ) continue;

            int railY = concentratedY(y1, y2, i, rampStart, rampEnd);
            int prevRailY = (i > 0) ? concentratedY(y1, y2, i - 1, rampStart, rampEnd) : railY;
            int nextRailY = (i < lastIdx) ? concentratedY(y1, y2, i + 1, rampStart, rampEnd) : railY;

            boolean gap = pointHash(segSeed, i, 1) < GAP_CHANCE;
            boolean torch = pointHash(segSeed, i, 2) < TORCH_CHANCE;
            int torchSide = pointHash(segSeed, i, 3) % 2;

            Direction toPrev = (i > 0) ? dirBetween(x, z, path[i-1][0], path[i-1][1]) : null;
            Direction toNext = (i < lastIdx) ? dirBetween(x, z, path[i+1][0], path[i+1][1]) : null;

            placePoint(world, x, railY, z, toPrev, toNext, prevRailY, nextRailY, gap, torch, torchSide, segSeed, i);
            placed = true;
        }
        return placed;
    }

    private boolean trySegment(StructureWorldAccess world, long seed,
                               int cx, int cz, int dir, int ncx, int ncz,
                               int minX, int minZ, int maxX, int maxZ) {

        if (!RailNetwork.hasConnection(seed, cx, cz, dir)) return false;

        int x1 = RailNetwork.nodeX(seed, cx, cz);
        int z1 = RailNetwork.nodeZ(seed, cx, cz);
        int y1 = RailNetwork.nodeY(seed, cx, cz);
        int x2 = RailNetwork.nodeX(seed, ncx, ncz);
        int z2 = RailNetwork.nodeZ(seed, ncx, ncz);
        int y2 = RailNetwork.nodeY(seed, ncx, ncz);

        int pad = 3;
        if (Math.max(x1, x2) + pad < minX || Math.min(x1, x2) - pad > maxX) return false;
        if (Math.max(z1, z2) + pad < minZ || Math.min(z1, z2) - pad > maxZ) return false;

        long segSeed = RailNetwork.segmentSeed(seed, cx, cz, dir);
        boolean xFirst = (dir == RailNetwork.DIR_EAST);
        int[][] path = buildLPath(x1, z1, x2, z2, xFirst);
        if (path.length < 2) return false;

        // ---- CONCENTRATED SLOPE PLANNING ----
        // If both nodes are on the same tier: perfectly flat, zero Y change.
        // If different tiers: flat at y1 for the first portion, then a continuous
        // ramp section of consecutive ascending/descending blocks, then flat at y2.
        int lastIdx = path.length - 1;
        int yDiff = y2 - y1;
        int absYDiff = Math.abs(yDiff);

        // Ramp occupies exactly |yDiff| blocks, centred at the midpoint.
        // Each ramp block changes Y by exactly 1 (a proper 45-degree slope).
        int rampStart = (lastIdx - absYDiff) / 2;
        int rampEnd = rampStart + absYDiff;
        // Clamp to path bounds
        if (rampStart < 0) rampStart = 0;
        if (rampEnd > lastIdx) rampEnd = lastIdx;

        boolean placed = false;

        for (int i = 0; i < path.length; i++) {
            int x = path[i][0];
            int z = path[i][1];
            if (x < minX || x > maxX || z < minZ || z > maxZ) continue;

            int plannedRailY = concentratedY(y1, y2, i, rampStart, rampEnd);

            // Over solid land (not water), bring elevated rail down to meet terrain.
            // Only descend — never climb up a hill (the tunnel model handles that).
            int groundY = world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE_WG, x, z);
            int railY;
            if (plannedRailY > groundY && groundY > world.getSeaLevel()) {
                // Ground is below us AND above sea level (land, not ocean floor)
                railY = groundY;
            } else {
                railY = plannedRailY;
            }

            int prevPlannedY = (i > 0) ? concentratedY(y1, y2, i - 1, rampStart, rampEnd) : plannedRailY;
            int nextPlannedY = (i < lastIdx) ? concentratedY(y1, y2, i + 1, rampStart, rampEnd) : plannedRailY;

            // Apply same ground-snap to prev/next for slope calculation
            int prevGroundY = Integer.MAX_VALUE;
            int nextGroundY = Integer.MAX_VALUE;
            if (i > 0) {
                prevGroundY = world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE_WG,
                        path[i-1][0], path[i-1][1]);
            }
            if (i < lastIdx) {
                nextGroundY = world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE_WG,
                        path[i+1][0], path[i+1][1]);
            }
            int prevRailY = (prevPlannedY > prevGroundY && prevGroundY > world.getSeaLevel())
                    ? prevGroundY : prevPlannedY;
            int nextRailY = (nextPlannedY > nextGroundY && nextGroundY > world.getSeaLevel())
                    ? nextGroundY : nextPlannedY;

            boolean gap = pointHash(segSeed, i, 1) < GAP_CHANCE;
            boolean torch = pointHash(segSeed, i, 2) < TORCH_CHANCE;
            int torchSide = pointHash(segSeed, i, 3) % 2;

            Direction toPrev = (i > 0) ? dirBetween(x, z, path[i-1][0], path[i-1][1]) : null;
            Direction toNext = (i < lastIdx) ? dirBetween(x, z, path[i+1][0], path[i+1][1]) : null;

            placePoint(world, x, railY, z, toPrev, toNext, prevRailY, nextRailY, gap, torch, torchSide, segSeed, i);
            placed = true;
        }
        return placed;
    }

    /**
     * Concentrated slope: flat at y1 before the ramp, stepping 1 per block through the
     * ramp, flat at y2 after the ramp. Same-tier segments (y1==y2) always return y1.
     */
    private static int concentratedY(int y1, int y2, int index, int rampStart, int rampEnd) {
        if (y1 == y2) return y1;
        if (index <= rampStart) return y1;
        if (index >= rampEnd) return y2;
        // Inside the ramp: step linearly from y1 toward y2
        int step = index - rampStart;
        int sign = (y2 > y1) ? 1 : -1;
        return y1 + sign * step;
    }

    private void placePoint(StructureWorldAccess world, int x, int railY, int z,
                            Direction toPrev, Direction toNext, int prevRailY, int nextRailY,
                            boolean gap, boolean torch, int torchSide,
                            long segSeed, int i) {
        BlockPos.Mutable m = new BlockPos.Mutable();

        boolean corner = (toPrev != null && toNext != null && toPrev.getOpposite() != toNext);
        Direction run = (toNext != null) ? toNext
                : (toPrev != null ? toPrev.getOpposite() : Direction.EAST);
        boolean alongX = run.getAxis() == Direction.Axis.X;

        int sideX = alongX ? 0 : 1;
        int sideZ = alongX ? 1 : 0;

        // ---- 1. CLEAR HEADROOM ----
        boolean anyGroundAbove = false;

        int oxMin = (corner || !alongX) ? -1 : 0;
        int oxMax = (corner || !alongX) ?  1 : 0;
        int ozMin = (corner ||  alongX) ? -1 : 0;
        int ozMax = (corner ||  alongX) ?  1 : 0;

        for (int ox = oxMin; ox <= oxMax; ox++) {
            for (int oz = ozMin; oz <= ozMax; oz++) {
                for (int oy = 0; oy < HEADROOM; oy++) {
                    m.set(x + ox, railY + oy, z + oz);
                    if (isProtected(world, m)) continue;
                    if (world.getBlockState(m).isSolidBlock(world, m)) anyGroundAbove = true;
                    // Never carve away rails or planks from a crossing segment
                    if (!isStructural(world.getBlockState(m))) {
                        world.setBlockState(m, Blocks.CAVE_AIR.getDefaultState(), 2);
                    }
                }
            }
        }

        // ---- 1b. EXTRA HEADROOM at slope positions ----
        // Ascending rails are 1 block taller, so clear 1 extra block above
        if (nextRailY != railY || prevRailY != railY) {
            for (int ox = oxMin; ox <= oxMax; ox++) {
                for (int oz = ozMin; oz <= ozMax; oz++) {
                    m.set(x + ox, railY + HEADROOM, z + oz);
                    if (!isProtected(world, m) && !isStructural(world.getBlockState(m))) {
                        world.setBlockState(m, Blocks.CAVE_AIR.getDefaultState(), 2);
                    }
                }
            }
        }

        // ---- 2. SUPPORT: centre plank + side fences at SAME Y ----
        m.set(x, railY - 1, z);
        boolean needsSupport = !world.getBlockState(m).isSolidBlock(world, m);

        if (needsSupport && !isProtected(world, m)) {
            // Centre plank — never overwrite an existing rail or plank
            if (!isStructural(world.getBlockState(m))) {
                world.setBlockState(m, Blocks.OAK_PLANKS.getDefaultState(), 2);
            }

            // Side fences at same Y as the plank
            for (int sign = -1; sign <= 1; sign += 2) {
                int fx = x + sideX * sign;
                int fz = z + sideZ * sign;
                m.set(fx, railY - 1, fz);
                if (!isProtected(world, m) && !isStructural(world.getBlockState(m))) {
                    world.setBlockState(m, connectedFence(toPrev, toNext), 2);
                }
            }
        }

        // ---- 3. Y-CHANGE: upper half slab + trestle posts ----
        if (nextRailY != railY) {
            int lowerY = Math.min(railY, nextRailY);

            // Upper half slab
            m.set(x, lowerY - 1, z);
            if (!world.getBlockState(m).isSolidBlock(world, m)
                    && !isProtected(world, m)
                    && !isStructural(world.getBlockState(m))) {
                world.setBlockState(m,
                        Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP), 2);
            }

            // 2-tall fence posts on each side
            for (int sign = -1; sign <= 1; sign += 2) {
                int fx = x + sideX * sign;
                int fz = z + sideZ * sign;
                for (int fy = lowerY - 1; fy <= lowerY; fy++) {
                    m.set(fx, fy, fz);
                    if (!isProtected(world, m) && !isStructural(world.getBlockState(m))) {
                        world.setBlockState(m, Blocks.OAK_FENCE.getDefaultState(), 2);
                    }
                }
            }
        }

        // ---- 4. RAIL ----
        if (!gap) {
            RailShape shape = flatShape(toPrev, toNext);

            if (toNext != null && nextRailY == railY + 1 && !isCurve(shape)) {
                shape = ascending(toNext);
            } else if (toPrev != null && prevRailY == railY + 1 && !isCurve(shape)) {
                shape = ascending(toPrev);
            }

            m.set(x, railY, z);
            if (!isStructural(world.getBlockState(m))) {
                world.setBlockState(m,
                        Blocks.RAIL.getDefaultState().with(RailBlock.SHAPE, shape), 2);
            }
        } else {
            // Gap position: 1/4 chance becomes a powered booster rail with redstone torches
            boolean booster = pointHash(segSeed, i, 4) < 25;
            if (booster && !isCurve(flatShape(toPrev, toNext))) {
                RailShape shape = flatShape(toPrev, toNext);
                if (toNext != null && nextRailY == railY + 1 && !isCurve(shape)) {
                    shape = ascending(toNext);
                } else if (toPrev != null && prevRailY == railY + 1 && !isCurve(shape)) {
                    shape = ascending(toPrev);
                }

                m.set(x, railY, z);
                if (!isStructural(world.getBlockState(m))) {
                    world.setBlockState(m,
                            Blocks.POWERED_RAIL.getDefaultState()
                                    .with(net.minecraft.block.PoweredRailBlock.SHAPE, shape)
                                    .with(net.minecraft.block.PoweredRailBlock.POWERED, true), 2);

                    // Redstone torches on both sides, at rail Y, on top of the fence posts
                    for (int sign = -1; sign <= 1; sign += 2) {
                        int tx = x + sideX * sign;
                        int tz = z + sideZ * sign;
                        m.set(tx, railY, tz);
                        if (!isProtected(world, m) && !isStructural(world.getBlockState(m))) {
                            world.setBlockState(m,
                                    Blocks.REDSTONE_TORCH.getDefaultState(), 2);
                        }
                    }
                }
            }
        }

        // ---- 5. TORCH only if solid wall exists behind it ----
        if (torch && !corner && anyGroundAbove) {
            int wx = alongX ? 0 : (torchSide == 0 ? -1 : 1);
            int wz = alongX ? (torchSide == 0 ? -1 : 1) : 0;

            int wallX = x + (alongX ? 0 : (torchSide == 0 ? -2 : 2));
            int wallZ = z + (alongX ? (torchSide == 0 ? -2 : 2) : 0);

            m.set(wallX, railY + 1, wallZ);
            boolean wallExists = world.getBlockState(m).isSolidBlock(world, m);

            if (wallExists) {
                m.set(x + wx, railY + 1, z + wz);
                if (world.getBlockState(m).isAir()) {
                    Direction facing = alongX
                            ? (torchSide == 0 ? Direction.SOUTH : Direction.NORTH)
                            : (torchSide == 0 ? Direction.EAST : Direction.WEST);
                    BlockState wt = unbetaWallTorch(facing);
                    if (wt != null) world.setBlockState(m, wt, 2);
                }
            }
        }
    }

    /** True if this block is a rail, plank, or slab — never overwrite these. */
    private boolean isStructural(BlockState state) {
        return state.getBlock() instanceof AbstractRailBlock
                || state.isOf(Blocks.OAK_PLANKS)
                || state.isOf(Blocks.OAK_SLAB);
    }

    private BlockState connectedFence(Direction toPrev, Direction toNext) {
        BlockState f = Blocks.OAK_FENCE.getDefaultState();
        if (toPrev != null) f = setFenceSide(f, toPrev);
        if (toNext != null) f = setFenceSide(f, toNext);
        return f;
    }

    private BlockState setFenceSide(BlockState fence, Direction d) {
        return switch (d) {
            case NORTH -> fence.with(FenceBlock.NORTH, true);
            case SOUTH -> fence.with(FenceBlock.SOUTH, true);
            case EAST  -> fence.with(FenceBlock.EAST, true);
            case WEST  -> fence.with(FenceBlock.WEST, true);
            default -> fence;
        };
    }

    private boolean isCurve(RailShape s) {
        return s == RailShape.NORTH_EAST || s == RailShape.NORTH_WEST
                || s == RailShape.SOUTH_EAST || s == RailShape.SOUTH_WEST;
    }

    private RailShape ascending(Direction toward) {
        return switch (toward) {
            case EAST -> RailShape.ASCENDING_EAST;
            case WEST -> RailShape.ASCENDING_WEST;
            case SOUTH -> RailShape.ASCENDING_SOUTH;
            default -> RailShape.ASCENDING_NORTH;
        };
    }

    private int[][] buildLPath(int x1, int z1, int x2, int z2, boolean xFirst) {
        int dx = Integer.signum(x2 - x1);
        int dz = Integer.signum(z2 - z1);
        int lenX = Math.abs(x2 - x1);
        int lenZ = Math.abs(z2 - z1);
        int[][] pts = new int[lenX + lenZ + 1][2];
        int n = 0, x = x1, z = z1;
        pts[n][0] = x; pts[n][1] = z; n++;
        if (xFirst) {
            for (int i = 0; i < lenX; i++) { x += dx; pts[n][0] = x; pts[n][1] = z; n++; }
            for (int i = 0; i < lenZ; i++) { z += dz; pts[n][0] = x; pts[n][1] = z; n++; }
        } else {
            for (int i = 0; i < lenZ; i++) { z += dz; pts[n][0] = x; pts[n][1] = z; n++; }
            for (int i = 0; i < lenX; i++) { x += dx; pts[n][0] = x; pts[n][1] = z; n++; }
        }
        return pts;
    }

    private Direction dirBetween(int x, int z, int tx, int tz) {
        if (tx > x) return Direction.EAST;
        if (tx < x) return Direction.WEST;
        if (tz > z) return Direction.SOUTH;
        return Direction.NORTH;
    }

    private RailShape flatShape(Direction a, Direction b) {
        if (a == null && b == null) return RailShape.NORTH_SOUTH;
        if (a == null) a = b.getOpposite();
        if (b == null) b = a.getOpposite();
        if (a.getOpposite() == b) {
            return (a.getAxis() == Direction.Axis.X) ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;
        }
        boolean north = (a == Direction.NORTH || b == Direction.NORTH);
        boolean south = (a == Direction.SOUTH || b == Direction.SOUTH);
        boolean east  = (a == Direction.EAST  || b == Direction.EAST);
        if (north && east) return RailShape.NORTH_EAST;
        if (north)         return RailShape.NORTH_WEST;
        if (south && east) return RailShape.SOUTH_EAST;
        return RailShape.SOUTH_WEST;
    }

    private static int pointHash(long segSeed, int index, int salt) {
        long h = segSeed * 6364136223846793005L
                + index * 1442695040888963407L
                + salt * 2654435761L;
        h ^= (h >>> 33); h *= 0xff51afd7ed558ccdL; h ^= (h >>> 33);
        return (int) Math.floorMod(h, 100);
    }

    private BlockState unbetaWallTorch(Direction facing) {
        try {
            var block = net.unbeta.content.torch.UnbetaTorchRegistry.WALL_TORCH;
            if (block == null) return null;
            return block.getDefaultState().with(WallTorchBlock.FACING, facing);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean isProtected(StructureWorldAccess world, BlockPos pos) {
        if (pos.getY() <= world.getBottomY() + 1) return true;
        return world.getBlockState(pos).isOf(Blocks.BEDROCK);
    }
}
