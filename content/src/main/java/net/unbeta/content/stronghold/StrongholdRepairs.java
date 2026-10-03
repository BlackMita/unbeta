package net.unbeta.content.stronghold;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.TorchBlock;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.structure.StructurePiece;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.unbeta.content.unmason.UnmasonEntity;
import net.unbeta.content.unmason.UnmasonRegistry;
import net.unbeta.content.zombie.RisingMob;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Dispatches Unmasons to repair stronghold breaches.
 *
 * <p>Once a second, for every open breach in a loaded area, a crew is assembled sized to
 * the damage: 1 mason for 1-12 blocks, 2 for 13-32, 3 for 33-64, 4 for 65 or more. Kept
 * small on purpose - masons crowding a hole get in each other's way. Unmasons already inside
 * the stronghold are recruited first, nearest first; the stronghold raises the rest out of
 * the floor, in the breach's own room or corridor where possible. Summoned masons stay on
 * afterwards as ordinary Unmasons.
 *
 * <p>Each mason CLAIMS the gap it's working on and the others skip it, so a crew spreads
 * across a hole instead of racing for the same block.
 *
 * <p>A mason that places nothing for GIVE_UP_TICKS is released from the job (and not
 * re-recruited for a while), so one stuck mason can't hold a breach hostage.
 */
public final class StrongholdRepairs {

    private static final double RESPOND_RANGE = 48.0;
    private static final long GIVE_UP_TICKS = 600;   // 30 s without placing anything
    private static final long BENCH_TICKS = 1200;    // then left out of recruiting for 60 s
    private static final int RISE_RADIUS = 8;
    private static final int RISE_HEIGHT = 4;

    /** Mason uuid -> centre of the breach it's repairing. */
    private static final Map<UUID, BlockPos> ASSIGNMENT = new HashMap<>();
    /** Mason uuid -> world time it last placed a block (or was assigned). */
    private static final Map<UUID, Long> LAST_PROGRESS = new HashMap<>();
    /** Mason uuid -> world time it may be recruited again. */
    private static final Map<UUID, Long> BENCHED = new HashMap<>();
    /** Gap -> the mason working on it. One gap per mason, one mason per gap. */
    private static final Map<BlockPos, UUID> CLAIMS = new HashMap<>();
    /** Breaches already told "no room" - said once, not every second. */
    private static final Set<BlockPos> NO_ROOM_ANNOUNCED = new HashSet<>();
    /** Open breaches as of the last check; repair goals read these. */
    private static List<Breach> current = new ArrayList<>();

    private StrongholdRepairs() {}

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(StrongholdRepairs::tick);
    }

    /** Crew size, scaled to the damage - and kept small, so masons don't crowd each other. */
    static int crewFor(Breach b) {
        int n = b.missing.size();
        if (n >= 65) return 4;
        if (n >= 33) return 3;
        if (n >= 13) return 2;
        return 1;
    }

    private static void tick(ServerWorld world) {
        if (world.getRegistryKey() != World.OVERWORLD) return;
        if (world.getTime() % 20 != 0) return;
        long now = world.getTime();

        // Drop positions that are no longer open; clear finished breaches.
        List<Breach> breaches = Breach.load(world);
        boolean changed = false;
        for (Iterator<Breach> it = breaches.iterator(); it.hasNext(); ) {
            Breach b = it.next();
            int before = b.missing.size();
            b.missing.entrySet().removeIf(e -> {
                BlockPos pos = e.getKey();
                if (isTorch(e.getValue())) return true; // never rebuilt (covers older saves)
                return loaded(world, pos) && !world.getBlockState(pos).isReplaceable();
            });
            if (b.missing.size() != before) changed = true;
            if (b.missing.isEmpty()) it.remove();
        }
        if (changed) Breach.save(world, breaches);
        current = breaches;

        // Release masons whose job is done, who are gone, or who have stalled.
        Set<BlockPos> open = new HashSet<>();
        for (Breach b : breaches) open.add(b.centre);
        ASSIGNMENT.entrySet().removeIf(e -> {
            UUID id = e.getKey();
            boolean gone = !open.contains(e.getValue())
                    || !(world.getEntity(id) instanceof UnmasonEntity u) || !u.isAlive();
            boolean stalled = now - LAST_PROGRESS.getOrDefault(id, now) > GIVE_UP_TICKS;
            if (stalled && !gone) BENCHED.put(id, now + BENCH_TICKS);
            if (gone || stalled) {
                LAST_PROGRESS.remove(id);
                return true;
            }
            return false;
        });
        BENCHED.values().removeIf(until -> until <= now);
        NO_ROOM_ANNOUNCED.retainAll(open);
        // Claims die with the mason's assignment, or once the gap is filled.
        CLAIMS.entrySet().removeIf(e -> !ASSIGNMENT.containsKey(e.getValue())
                || (loaded(world, e.getKey()) && !world.getBlockState(e.getKey()).isReplaceable()));

        for (Breach b : breaches) {
            if (!loaded(world, b.centre)) continue;
            int want = crewFor(b);
            int crew = (int) ASSIGNMENT.values().stream().filter(b.centre::equals).count();
            if (crew >= want) continue;

            // Recruit Unmasons already inside the stronghold - ones out in the caves usually
            // can't path in.
            Vec3d centre = Vec3d.ofCenter(b.centre);
            List<UnmasonEntity> nearby = world.getEntitiesByClass(UnmasonEntity.class,
                    new Box(b.centre).expand(RESPOND_RANGE),
                    u -> u.isAlive() && !ASSIGNMENT.containsKey(u.getUuid())
                            && !BENCHED.containsKey(u.getUuid())
                            && StrongholdSpace.isInside(world, u.getBlockPos()));
            nearby.sort(Comparator.comparingDouble(u -> u.squaredDistanceTo(centre)));
            for (UnmasonEntity u : nearby) {
                if (crew >= want) break;
                if (u.squaredDistanceTo(centre) > RESPOND_RANGE * RESPOND_RANGE) continue;
                assign(u, b.centre, now);
                crew++;
            }

            // The stronghold raises the rest.
            int raised = 0;
            for (BlockPos spot : riseSpots(world, b, want - crew)) {
                UnmasonEntity m = raise(world, spot);
                if (m == null) continue;
                assign(m, b.centre, now);
                crew++;
                raised++;
            }

            if (Breach.announce) {
                if (raised > 0) {
                    announce(world, "[breach] the stronghold raised " + raised + " mason(s)");
                } else if (crew == 0 && NO_ROOM_ANNOUNCED.add(b.centre)) {
                    announce(world, "[breach] no room to raise a mason near "
                            + b.centre.getX() + "," + b.centre.getY() + "," + b.centre.getZ());
                }
            }
        }
    }

    private static boolean loaded(ServerWorld world, BlockPos pos) {
        return world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4);
    }

    private static void assign(UnmasonEntity mason, BlockPos centre, long now) {
        ASSIGNMENT.put(mason.getUuid(), centre);
        LAST_PROGRESS.put(mason.getUuid(), now);
    }

    /** The breach this mason is assigned to, or null. */
    public static Breach jobFor(UnmasonEntity mason) {
        BlockPos centre = ASSIGNMENT.get(mason.getUuid());
        if (centre == null) return null;
        for (Breach b : current) if (b.centre.equals(centre)) return b;
        return null;
    }

    // ------------------------------------------------------------------ claims

    /** True if another mason is already working on this gap. */
    public static boolean claimedByOther(UnmasonEntity mason, BlockPos gap) {
        UUID owner = CLAIMS.get(gap);
        return owner != null && !owner.equals(mason.getUuid());
    }

    /** This mason now works on this gap, and only this gap. */
    public static void claim(UnmasonEntity mason, BlockPos gap) {
        UUID id = mason.getUuid();
        CLAIMS.values().removeIf(id::equals);
        CLAIMS.put(gap, id);
    }

    public static void releaseClaims(UnmasonEntity mason) {
        UUID id = mason.getUuid();
        CLAIMS.values().removeIf(id::equals);
    }

    /**
     * A mason put a block back. Re-reads the saved breaches rather than saving the cached
     * list, so a breach recorded since the last check can't be lost.
     */
    public static void placed(ServerWorld world, UnmasonEntity mason, BlockPos centre, BlockPos pos) {
        LAST_PROGRESS.put(mason.getUuid(), world.getTime());
        CLAIMS.remove(pos);
        List<Breach> all = Breach.load(world);
        for (Iterator<Breach> it = all.iterator(); it.hasNext(); ) {
            Breach b = it.next();
            if (!b.centre.equals(centre)) continue;
            b.missing.remove(pos);
            if (b.missing.isEmpty()) it.remove();
        }
        Breach.save(world, all);
        for (Breach b : current) if (b.centre.equals(centre)) b.missing.remove(pos);
    }

    /**
     * What a mason puts back: the masonry exactly as it was (stair facing, slab half, mossy
     * stays mossy, infested stays infested). Doors are bricked over. Torches never reach
     * here - they aren't stonework, so breaking one is no breach at all.
     */
    public static BlockState replacementFor(BlockState original) {
        if (original.getBlock() instanceof DoorBlock) {
            return Blocks.STONE_BRICKS.getDefaultState();
        }
        if (original.contains(Properties.WATERLOGGED)) {
            original = original.with(Properties.WATERLOGGED, false);
        }
        return original;
    }

    private static boolean isTorch(BlockState state) {
        return state.getBlock() instanceof TorchBlock
                || state.getBlock() instanceof net.unbeta.content.torch.UnbetaTorchBlock
                || state.getBlock() instanceof net.unbeta.content.torch.UnbetaWallTorchBlock;
    }

    private static void announce(ServerWorld world, String msg) {
        world.getPlayers().forEach(p -> p.sendMessage(Text.literal(msg).formatted(Formatting.GOLD), false));
    }

    // ------------------------------------------------------------------ summoning

    /**
     * Up to `count` distinct spots to raise masons: floor with standing room, inside the
     * stronghold, nobody already there. Every candidate is checked (no random guessing),
     * preferring the breach's own room or corridor - so a mason never rises on the far
     * side of a wall it can't path through - then nearest first.
     */
    private static List<BlockPos> riseSpots(ServerWorld world, Breach b, int count) {
        List<BlockPos> out = new ArrayList<>();
        if (count <= 0) return out;
        StructurePiece home = StrongholdSpace.pieceAt(world, b.centre);

        List<BlockPos> sameRoom = new ArrayList<>();
        List<BlockPos> elsewhere = new ArrayList<>();
        for (int dx = -RISE_RADIUS; dx <= RISE_RADIUS; dx++) {
            for (int dy = -RISE_HEIGHT; dy <= RISE_HEIGHT; dy++) {
                for (int dz = -RISE_RADIUS; dz <= RISE_RADIUS; dz++) {
                    BlockPos p = b.centre.add(dx, dy, dz);
                    if (b.missing.containsKey(p)) continue;
                    if (!isPassable(world, p) || !isPassable(world, p.up())) continue;
                    if (!world.getBlockState(p.down()).isSolidBlock(world, p.down())) continue;
                    StructurePiece piece = StrongholdSpace.pieceAt(world, p);
                    if (piece == null) continue; // not inside the stronghold
                    if (!world.getEntitiesByClass(LivingEntity.class, new Box(p), LivingEntity::isAlive).isEmpty()) continue;
                    if (home != null && piece.getBoundingBox().equals(home.getBoundingBox())) sameRoom.add(p);
                    else elsewhere.add(p);
                }
            }
        }
        Comparator<BlockPos> nearest = Comparator.comparingDouble(p -> p.getSquaredDistance(b.centre));
        sameRoom.sort(nearest);
        elsewhere.sort(nearest);
        List<BlockPos> ordered = new ArrayList<>(sameRoom);
        ordered.addAll(elsewhere);

        for (BlockPos p : ordered) {
            if (out.size() >= count) break;
            if (out.stream().anyMatch(q -> q.getSquaredDistance(p) < 2)) continue; // room to stand apart
            out.add(p);
        }
        return out;
    }

    private static UnmasonEntity raise(ServerWorld world, BlockPos spot) {
        UnmasonEntity mason = UnmasonRegistry.UNMASON.create(world);
        if (mason == null) return null;
        mason.refreshPositionAndAngles(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5,
                world.random.nextFloat() * 360.0F, 0.0F);
        RisingMob.prePosition(mason, spot); // buried before it's ever seen
        world.spawnEntity(mason);
        RisingMob.begin(mason, world, spot);
        world.playSound(null, spot, SoundEvents.BLOCK_STONE_BREAK, SoundCategory.HOSTILE, 1.0F, 0.6F);
        return mason;
    }

    private static boolean isPassable(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.getCollisionShape(world, pos).isEmpty() && state.getFluidState().isEmpty();
    }
}
