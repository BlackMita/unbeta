package net.unbeta.content.stronghold;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.unbeta.core.state.UnbetaWorldState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Damage to the stronghold's own masonry, waiting to be repaired.
 *
 * <p>One breach is one EVENT, not one block: an explosion that takes out eight blocks is a
 * single breach centred on the blast, so eight masons aren't summoned for one creeper.
 *
 * <p>Only blocks in the stronghold_stonework tag are recorded, and only inside the
 * stronghold itself - the masons restore the building, not the mountain around it.
 * Breaches are saved with the world.
 */
public final class Breach {

    public static final TagKey<Block> STONEWORK = TagKey.of(RegistryKeys.BLOCK,
            new Identifier("unbeta-content", "stronghold_stonework"));

    private static final String SECTION = "StrongholdBreaches";
    /** Damage within this many blocks of an existing breach joins it, so a mining spree is one job. */
    private static final double MERGE_RADIUS = 8.0;
    /** TEMP, for testing: announce each breach as it's recorded. */
    public static boolean announce = true;

    /** Where the damage was centred - where the masons head for. */
    public final BlockPos centre;
    /** Position -> the block that used to be there. */
    public final Map<BlockPos, BlockState> missing;

    public Breach(BlockPos centre, Map<BlockPos, BlockState> missing) {
        this.centre = centre;
        this.missing = missing;
    }

    /**
     * Record one event. Positions not in the stronghold, or not stonework, are ignored;
     * if nothing is left, no breach is recorded.
     */
    public static void record(ServerWorld world, List<BlockPos> positions) {
        // Explosions: the blocks are still in place, so read them now.
        Map<BlockPos, BlockState> states = new LinkedHashMap<>();
        for (BlockPos pos : positions) states.put(pos.toImmutable(), world.getBlockState(pos));
        record(world, states);
    }

    /**
     * Record one event from positions AND the blocks that were there. The caller supplies
     * the states because a player break is reported only after the block is gone - reading
     * the world at that point just finds air, which isn't stonework, so nothing recorded.
     */
    public static void record(ServerWorld world, Map<BlockPos, BlockState> states) {
        Map<BlockPos, BlockState> missing = new LinkedHashMap<>();
        for (var entry : states.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState state = entry.getValue();
            if (!state.isIn(STONEWORK)) continue;
            if (!StrongholdSpace.isInside(world, pos)) continue;
            missing.put(pos.toImmutable(), state);
            // A door is two blocks, and breaking either half removes both - so record the
            // other half too, or the masons would brick the bottom and leave the top open.
            if (state.getBlock() instanceof net.minecraft.block.DoorBlock
                    && state.contains(net.minecraft.block.DoorBlock.HALF)) {
                boolean lower = state.get(net.minecraft.block.DoorBlock.HALF)
                        == net.minecraft.block.enums.DoubleBlockHalf.LOWER;
                BlockPos other = lower ? pos.up() : pos.down();
                if (StrongholdSpace.isInside(world, other)) {
                    missing.putIfAbsent(other.toImmutable(), state.with(net.minecraft.block.DoorBlock.HALF,
                            lower ? net.minecraft.block.enums.DoubleBlockHalf.UPPER
                                  : net.minecraft.block.enums.DoubleBlockHalf.LOWER));
                }
            }
        }
        if (missing.isEmpty()) return;

        BlockPos centre = centreOf(missing.keySet());
        List<Breach> all = load(world);
        // Damage near an existing breach joins it: one job, one crew, sized to the whole thing.
        Breach joined = null;
        for (Breach b : all) {
            if (b.centre.getSquaredDistance(centre) <= MERGE_RADIUS * MERGE_RADIUS) {
                joined = b;
                break;
            }
        }
        if (joined != null) {
            for (var e : missing.entrySet()) joined.missing.putIfAbsent(e.getKey(), e.getValue());
        } else {
            all.add(new Breach(centre, missing));
        }
        save(world, all);

        if (announce) {
            String msg = joined != null
                    ? "[breach] +" + missing.size() + " block(s), joined breach at "
                      + joined.centre.getX() + "," + joined.centre.getY() + "," + joined.centre.getZ()
                      + " (now " + joined.missing.size() + ")"
                    : "[breach] " + missing.size() + " block(s) at "
                      + centre.getX() + "," + centre.getY() + "," + centre.getZ();
            world.getPlayers().forEach(p -> p.sendMessage(Text.literal(msg).formatted(Formatting.GOLD), false));
        }
    }

    private static BlockPos centreOf(Iterable<BlockPos> positions) {
        long x = 0, y = 0, z = 0;
        int n = 0;
        for (BlockPos p : positions) { x += p.getX(); y += p.getY(); z += p.getZ(); n++; }
        return new BlockPos((int)(x / n), (int)(y / n), (int)(z / n));
    }

    // ------------------------------------------------------------------ persistence

    public static List<Breach> load(ServerWorld world) {
        List<Breach> out = new ArrayList<>();
        NbtCompound root = UnbetaWorldState.read(world, SECTION);
        NbtList list = root.getList("breaches", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound e = list.getCompound(i);
            BlockPos centre = BlockPos.fromLong(e.getLong("centre"));
            Map<BlockPos, BlockState> missing = new LinkedHashMap<>();
            NbtList blocks = e.getList("blocks", NbtElement.COMPOUND_TYPE);
            for (int j = 0; j < blocks.size(); j++) {
                NbtCompound b = blocks.getCompound(j);
                BlockState state;
                if (b.contains("state", NbtElement.COMPOUND_TYPE)) {
                    // The full state: stair facing, slab half, and so on.
                    state = net.minecraft.nbt.NbtHelper.toBlockState(
                            Registries.BLOCK.getReadOnlyWrapper(), b.getCompound("state"));
                } else {
                    // Older saves kept only the block's name.
                    state = Registries.BLOCK.get(new Identifier(b.getString("id"))).getDefaultState();
                }
                missing.put(BlockPos.fromLong(b.getLong("pos")), state);
            }
            if (!missing.isEmpty()) out.add(new Breach(centre, missing));
        }
        return out;
    }

    public static void save(ServerWorld world, List<Breach> breaches) {
        NbtList list = new NbtList();
        for (Breach breach : breaches) {
            NbtCompound e = new NbtCompound();
            e.putLong("centre", breach.centre.asLong());
            NbtList blocks = new NbtList();
            for (var entry : breach.missing.entrySet()) {
                NbtCompound b = new NbtCompound();
                b.putLong("pos", entry.getKey().asLong());
                b.putString("id", Registries.BLOCK.getId(entry.getValue().getBlock()).toString());
                b.put("state", net.minecraft.nbt.NbtHelper.fromBlockState(entry.getValue()));
                blocks.add(b);
            }
            e.put("blocks", blocks);
            list.add(e);
        }
        UnbetaWorldState.edit(world, SECTION, nbt -> nbt.put("breaches", list));
    }
}
