package net.unbeta.content.stronghold;

import net.minecraft.registry.tag.StructureTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

/**
 * "Is this position inside the stronghold?"
 *
 * <p>Not chunk membership: a stronghold's overall bounding box is a huge cube that
 * includes all the solid rock between its corridors, and a single chunk can hold a sliver
 * of stronghold or none at all. What counts is the individual generated PIECES - the
 * corridors, rooms, stairs and the portal room - so a position is inside the stronghold
 * only when it falls within one of those pieces.
 *
 * <p>Piece boxes are grown by MARGIN so standing in a doorway, on a threshold or flush
 * against a wall still counts.
 */
public final class StrongholdSpace {

    /** Blocks of slack around each piece, so doorways and wall-hugging still count. */
    private static final int MARGIN = 1;

    private StrongholdSpace() {}

    public static boolean isInside(World world, BlockPos pos) {
        return pieceAt(world, pos) != null;
    }

    /** The stronghold piece containing this position, or null. */
    @Nullable
    public static StructurePiece pieceAt(World world, BlockPos pos) {
        if (!(world instanceof ServerWorld sw)) return null;
        StructureStart start = sw.getStructureAccessor()
                .getStructureContaining(pos, StructureTags.EYE_OF_ENDER_LOCATED);
        if (start == null || !start.hasChildren()) return null;
        for (StructurePiece piece : start.getChildren()) {
            if (expand(piece.getBoundingBox()).contains(pos)) return piece;
        }
        return null;
    }

    /**
     * The stronghold containing this position, without our doorway margin.
     *
     * <p>Vanilla's getStructureContaining is itself piece-level - it walks the children and
     * tests each piece's box - so this never matches a position in the solid rock between
     * corridors. pieceAt is the same test plus MARGIN, and names the piece.
     */
    @Nullable
    public static StructureStart startAt(World world, BlockPos pos) {
        if (!(world instanceof ServerWorld sw)) return null;
        StructureStart start = sw.getStructureAccessor()
                .getStructureContaining(pos, StructureTags.EYE_OF_ENDER_LOCATED);
        return start != null && start.hasChildren() ? start : null;
    }

    private static BlockBox expand(BlockBox box) {
        return new BlockBox(
                box.getMinX() - MARGIN, box.getMinY() - MARGIN, box.getMinZ() - MARGIN,
                box.getMaxX() + MARGIN, box.getMaxY() + MARGIN, box.getMaxZ() + MARGIN);
    }
}
