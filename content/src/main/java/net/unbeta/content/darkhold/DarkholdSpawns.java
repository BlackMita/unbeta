package net.unbeta.content.darkhold;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.Structure;

import java.util.List;

/**
 * Endermen crowd a Darkhold. While anyone is on its grounds, an Enderman appears every second
 * or so - in its rooms, its stairwells, its towers or on the ground around it, never right next
 * to a player - until about a dozen are about. Light doesn't stop them. (Every other kind of
 * mob is kept off the grounds by the structure's own spawn rules.)
 */
public final class DarkholdSpawns {

    private static final int EVERY = 20;          // ticks between attempts
    private static final int CAP = 12;            // Endermen on and around the grounds
    private static final int AROUND = 16;         // "around": this far past the Darkhold's box
    private static final double NOT_TOO_CLOSE = 6.0;

    private DarkholdSpawns() {}

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (world.getRegistryKey() != World.OVERWORLD || world.getTime() % EVERY != 0) return;
            for (ServerPlayerEntity player : world.getPlayers()) {
                if (player.isSpectator()) continue;
                StructureStart start = startAt(world, player.getBlockPos());
                if (start != null) tick(world, start);
            }
        });
    }

    /** The Darkhold whose box holds this position, or null. */
    public static StructureStart startAt(ServerWorld world, BlockPos pos) {
        Structure darkhold = world.getRegistryManager().get(RegistryKeys.STRUCTURE).get(Darkhold.ID);
        if (darkhold == null) return null;
        StructureStart start = world.getStructureAccessor().getStructureAt(pos, darkhold);
        return start != null && start.hasChildren() ? start : null;
    }

    private static void tick(ServerWorld world, StructureStart start) {
        BlockBox bb = start.getBoundingBox();
        Box area = new Box(bb.getMinX() - AROUND, bb.getMinY() - 8, bb.getMinZ() - AROUND,
                bb.getMaxX() + AROUND + 1, bb.getMaxY() + 8, bb.getMaxZ() + AROUND + 1);
        if (world.getEntitiesByType(EntityType.ENDERMAN, area, e -> true).size() >= CAP) return;
        List<StructurePiece> pieces = start.getChildren();
        for (int attempt = 0; attempt < 8; attempt++) {
            BlockPos spot = world.random.nextInt(4) == 0 ? outside(world, bb) : inside(world, pieces);
            if (spot == null || !fits(world, spot)) continue;
            if (world.getClosestPlayer(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, NOT_TOO_CLOSE, false) != null) continue;
            EndermanEntity enderman = EntityType.ENDERMAN.create(world);
            if (enderman == null) return;
            enderman.refreshPositionAndAngles(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5,
                    world.random.nextFloat() * 360.0F, 0.0F);
            enderman.initialize(world, world.getLocalDifficulty(spot), SpawnReason.NATURAL, null, null);
            world.spawnEntityAndPassengers(enderman);
            return;
        }
    }

    /** A random standing spot inside one of the rooms, stairwells or towers. */
    private static BlockPos inside(ServerWorld world, List<StructurePiece> pieces) {
        StructurePiece piece = pieces.get(world.random.nextInt(pieces.size()));
        if (!(piece instanceof DarkholdRoomPiece || piece instanceof DarkholdStairPiece
                || piece instanceof DarkholdTowerPiece)) return null;
        BlockBox b = piece.getBoundingBox();
        int x = b.getMinX() + 1 + world.random.nextInt(Math.max(1, b.getBlockCountX() - 2));
        int z = b.getMinZ() + 1 + world.random.nextInt(Math.max(1, b.getBlockCountZ() - 2));
        int y = Math.max(b.getMinY(), world.getBottomY()) + 1 + world.random.nextInt(Math.max(1, Math.min(b.getBlockCountY(), 48) - 3));
        for (int down = 0; down < 10; down++, y--) {
            BlockPos p = new BlockPos(x, y, z);
            if (fits(world, p)) return p;
        }
        return null;
    }

    /** A random spot on the ground around the Darkhold. */
    private static BlockPos outside(ServerWorld world, BlockBox bb) {
        int x = bb.getMinX() - AROUND + world.random.nextInt(bb.getBlockCountX() + 2 * AROUND);
        int z = bb.getMinZ() - AROUND + world.random.nextInt(bb.getBlockCountZ() + 2 * AROUND);
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return null;
        return new BlockPos(x, world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    /** Solid ground below, three blocks of room for an Enderman, loaded, dry. */
    private static boolean fits(ServerWorld world, BlockPos p) {
        if (!world.isChunkLoaded(p.getX() >> 4, p.getZ() >> 4)) return false;
        if (!world.getBlockState(p.down()).isSolidBlock(world, p.down())) return false;
        for (int k = 0; k < 3; k++) {
            BlockPos q = p.up(k);
            if (!world.getBlockState(q).getCollisionShape(world, q).isEmpty() || !world.getFluidState(q).isEmpty()) return false;
        }
        return true;
    }
}
