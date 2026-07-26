package net.unbeta.core.gates;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import net.unbeta.core.UnbetaCore;
import net.unbeta.core.api.ContentKind;
import net.unbeta.core.api.UnbetaApi;
import net.unbeta.core.config.UnbetaConfig;

import java.util.HashSet;
import java.util.Set;

/**
 * Replaces gated blocks with stone when a chunk loads - the safety net for gated
 * blocks placed by biome carvers/decorators (lush-cave vegetation) that the feature
 * gate cannot reach.
 *
 * <h2>Performance</h2>
 * The naive version of this melted the server: scanning every block of every chunk on
 * the server thread falls fatally behind as chunks stream in. This version is cheap:
 * <ul>
 *   <li><b>Section pre-check.</b> A {@link ChunkSection} exposes {@code hasrandom ticking}
 *       and, more usefully, {@code isEmpty()} plus a palette. We skip any section whose
 *       palette contains no gated block at all - so an all-stone or all-air 16^3 section
 *       costs one palette scan (a few entries) instead of 4096 block reads.</li>
 *   <li><b>Per-chunk memo.</b> Each chunk is scanned at most once per session.</li>
 *   <li><b>Bounded height.</b> Only sections overlapping [bottomY, scanBelowY] are looked at.</li>
 * </ul>
 * In practice almost every underground section is a single-entry stone palette and is
 * skipped instantly; only the rare section that actually contains lush-cave blocks is
 * scanned block-by-block.
 */
public final class GeneratedBlockGate {

    private static final BlockState STONE = Blocks.STONE.getDefaultState();

    // Chunks already handled this session: packed (x,z) longs.
    private static final Set<Long> DONE = new HashSet<>();

    private GeneratedBlockGate() {}

    public static void register(UnbetaConfig config) {
        if (!config.replaceGeneratedGatedBlocks) {
            UnbetaCore.LOG.info("Generated-block gate disabled by config.");
            return;
        }
        final int scanBelowY = config.scanBelowY;

        ServerChunkEvents.CHUNK_LOAD.register((world, chunk) ->
                scanChunk(world, chunk, scanBelowY, config.verboseGateLogging));

        UnbetaCore.LOG.info("Generated-block gate active (section-aware; gated blocks -> stone up to Y={}).",
                scanBelowY);
    }

    private static void scanChunk(ServerWorld world, WorldChunk chunk, int scanBelowY, boolean verbose) {
        if (!UnbetaApi.isReady()) return;

        long key = chunk.getPos().toLong();
        if (!DONE.add(key)) return; // already handled

        ChunkSection[] sections = chunk.getSectionArray();
        int bottomY = world.getBottomY();
        int minX = chunk.getPos().getStartX();
        int minZ = chunk.getPos().getStartZ();

        int swapped = 0;
        BlockPos.Mutable pos = new BlockPos.Mutable();

        for (int i = 0; i < sections.length; i++) {
            ChunkSection section = sections[i];
            if (section == null || section.isEmpty()) continue;

            int sectionBottom = bottomY + (i << 4);
            if (sectionBottom > scanBelowY) continue; // whole section above the scan ceiling

            // Cheap pre-check: does this section's palette contain ANY gated block?
            if (!sectionHasGatedBlock(section)) continue;

            int yStart = sectionBottom;
            int yEnd = Math.min(sectionBottom + 15, scanBelowY);
            for (int y = yStart; y <= yEnd; y++) {
                for (int dx = 0; dx < 16; dx++) {
                    for (int dz = 0; dz < 16; dz++) {
                        BlockState state = section.getBlockState(dx, y - sectionBottom, dz);
                        if (state.isAir()) continue;
                        if (isGated(state)) {
                            pos.set(minX + dx, y, minZ + dz);
                            chunk.setBlockState(pos, STONE, false);
                            swapped++;
                        }
                    }
                }
            }
        }

        if (swapped > 0) {
            chunk.setNeedsSaving(true);
            if (verbose) {
                UnbetaCore.LOG.debug("Generated-block gate swapped {} block(s) in chunk {},{}",
                        swapped, chunk.getPos().x, chunk.getPos().z);
            }
        }
    }

    /** True if any entry in the section's block palette is a gated block. */
    private static boolean sectionHasGatedBlock(ChunkSection section) {
        // hasAny walks the palette, not all 4096 positions.
        return section.getBlockStateContainer().hasAny(GeneratedBlockGate::isGated);
    }

    private static boolean isGated(BlockState state) {
        Block block = state.getBlock();
        Identifier id = Registries.BLOCK.getId(block);
        if (!UnbetaApi.rules().isGatedNamespace(id.getNamespace())) return false;
        return UnbetaApi.rules().isRemoved(ContentKind.BLOCK, id);
    }
}
