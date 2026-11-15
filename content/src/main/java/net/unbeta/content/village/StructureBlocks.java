package net.unbeta.content.village;

import net.minecraft.block.AbstractSignBlock;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EmptyBlockView;
import net.unbeta.core.api.ContentKind;
import net.unbeta.core.api.UnbetaApi;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What a structure (a village above all) builds with in place of a block that isn't in Unbeta.
 * Each block not in Unbeta gets its nearest Unbeta block: oak for other woods, plain glass for
 * stained glass, a furnace for a smoker or blast furnace, a crafting table for the other work
 * tables, a chest for a barrel (its loot kept), snow for packed and blue ice, cobblestone or
 * stone for the fancy stones. Job blocks with no Unbeta match (bells, lecterns, looms,
 * grindstones, brewing stands), banners and campfires are left out. Shapes and facings carry
 * over (stairs stay stairs, doors stay doors, the way they faced). Anything else: cobblestone
 * if it was a solid block, else nothing.
 */
public final class StructureBlocks {

    private StructureBlocks() {}

    /** The answer for each block, worked out once: the block to use, or AIR to leave it out. */
    private static final Map<Block, Block> CHOICE = new ConcurrentHashMap<>();

    private static final String[] WOODS = {"spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "bamboo", "crimson", "warped"};
    private static final String[] FANCY_STONES = {"polished_granite", "polished_diorite", "polished_andesite", "granite", "diorite",
            "andesite", "smooth_stone", "stone_brick", "mossy_stone_brick", "cracked_stone_brick", "deepslate_brick", "deepslate_tile",
            "polished_deepslate", "cobbled_deepslate", "deepslate", "polished_blackstone_brick", "polished_blackstone", "blackstone",
            "mud_brick", "brick", "tuff", "calcite", "quartz", "smooth_quartz", "prismarine", "purpur", "end_stone_brick"};
    private static final Map<String, List<String>> NAMED = Map.ofEntries(
            Map.entry("smoker", List.of("furnace")),
            Map.entry("blast_furnace", List.of("furnace")),
            Map.entry("fletching_table", List.of("crafting_table")),
            Map.entry("smithing_table", List.of("crafting_table")),
            Map.entry("cartography_table", List.of("crafting_table")),
            Map.entry("barrel", List.of("chest")),
            Map.entry("packed_ice", List.of("snow_block")),
            Map.entry("blue_ice", List.of("snow_block")),
            Map.entry("smooth_stone", List.of("stone")),
            Map.entry("water_cauldron", List.of("cauldron")),
            Map.entry("lantern", List.of("torch")),
            Map.entry("soul_lantern", List.of("torch")),
            Map.entry("tall_grass", List.of("grass")),
            Map.entry("large_fern", List.of("fern")),
            Map.entry("beetroots", List.of("wheat")),
            Map.entry("melon_stem", List.of("wheat")),
            Map.entry("pumpkin_stem", List.of("wheat")),
            Map.entry("attached_melon_stem", List.of("wheat")),
            Map.entry("attached_pumpkin_stem", List.of("wheat")),
            Map.entry("brown_mushroom_block", List.of("dirt")),
            Map.entry("packed_mud", List.of("dirt")),
            Map.entry("coarse_dirt", List.of("dirt")),
            Map.entry("rooted_dirt", List.of("dirt")),
            Map.entry("podzol", List.of("dirt")),
            Map.entry("mud", List.of("dirt")),
            Map.entry("suspicious_sand", List.of("sand")),
            Map.entry("suspicious_gravel", List.of("gravel")),
            Map.entry("light_gray_wool", List.of("white_wool"))
    );
    /** Left out entirely (no Unbeta block plays their part). */
    private static final java.util.Set<String> DROPPED = java.util.Set.of("bell", "lectern", "loom", "grindstone", "brewing_stand",
            "campfire", "soul_campfire", "sea_pickle", "composter", "jigsaw", "structure_void");

    /**
     * The block to put in place of this one, or null to keep it as it is. A state of air means
     * leave it out.
     */
    public static BlockState swap(BlockState state) {
        Block block = state.getBlock();
        if (state.isAir() || block == Blocks.JIGSAW || block == Blocks.STRUCTURE_VOID || block == Blocks.STRUCTURE_BLOCK) return null;
        if (!UnbetaApi.isReady() || !removed(block)) return null;
        Block to = CHOICE.computeIfAbsent(block, b -> choose(b, state));
        if (to == block) return null;
        if (to == Blocks.AIR) return Blocks.AIR.getDefaultState();
        // The top half of tall grass or a large fern: the short one only needs the bottom.
        if (state.contains(Properties.DOUBLE_BLOCK_HALF) && state.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER
                && !to.getDefaultState().contains(Properties.DOUBLE_BLOCK_HALF)) {
            return Blocks.AIR.getDefaultState();
        }
        // A hanging lantern can't become a torch: it's left out.
        if (state.contains(Properties.HANGING) && state.get(Properties.HANGING) && to == Blocks.TORCH) return Blocks.AIR.getDefaultState();
        BlockState out = carry(state, to.getDefaultState());
        // Crops: as far grown as they were.
        if (state.contains(Properties.AGE_3) && out.contains(Properties.AGE_7)) out = out.with(Properties.AGE_7, state.get(Properties.AGE_3) * 2 + 1);
        return out;
    }

    /** Should the block entity data (a sign's writing, a container's loot) come along? */
    public static boolean keepsData(BlockState from, BlockState to) {
        Block a = from.getBlock(), b = to.getBlock();
        return (a instanceof AbstractSignBlock && b instanceof AbstractSignBlock)
                || ((a instanceof BarrelBlock || a instanceof ChestBlock) && b instanceof ChestBlock);
    }

    /** Not in Unbeta: the block is removed, and so is its item (stripped logs, say, are kept as items, so they stay). */
    private static boolean removed(Block block) {
        Identifier id = Registries.BLOCK.getId(block);
        if (!UnbetaApi.rules().isGatedNamespace(id.getNamespace()) || !UnbetaApi.rules().isRemoved(ContentKind.BLOCK, id)) return false;
        net.minecraft.item.Item item = block.asItem();
        return item == net.minecraft.item.Items.AIR || UnbetaApi.rules().isRemoved(ContentKind.ITEM, Registries.ITEM.getId(item));
    }

    /** A vanilla block by name, if it exists and is in Unbeta. */
    private static Optional<Block> ok(String path) {
        Identifier id = new Identifier("minecraft", path);
        if (!Registries.BLOCK.containsId(id)) return Optional.empty();
        Block b = Registries.BLOCK.get(id);
        return removed(b) ? Optional.empty() : Optional.of(b);
    }

    private static Block choose(Block block, BlockState state) {
        String path = Registries.BLOCK.getId(block).getPath();
        if (DROPPED.contains(path) || path.endsWith("_banner") || path.endsWith("_wall_banner")) return Blocks.AIR;
        for (String c : NAMED.getOrDefault(path, List.of())) {
            Optional<Block> b = ok(c);
            if (b.isPresent()) return b.get();
        }
        // Stained glass: plain glass.
        if (path.endsWith("_stained_glass_pane")) return ok("glass_pane").orElse(Blocks.AIR);
        if (path.endsWith("_stained_glass")) return ok("glass").orElse(Blocks.AIR);
        // Saplings and potted plants.
        if (path.startsWith("potted_")) return ok("flower_pot").orElse(Blocks.AIR);
        if (path.endsWith("_sapling")) return ok("oak_sapling").orElse(Blocks.AIR);
        // Other woods: the same piece in that wood if Unbeta has it (a spruce log), else in oak.
        for (String wood : WOODS) {
            if (!path.contains(wood + "_")) continue;
            String rest = path.replace(wood + "_", "");
            String mine = path;
            if (rest.endsWith("wood") || rest.endsWith("hyphae")) {          // the all-bark block: the log
                mine = path.replace("_wood", "_log").replace("_hyphae", "_stem");
                rest = rest.replace("wood", "log").replace("hyphae", "log");
            }
            if (rest.endsWith("stem")) rest = rest.replace("stem", "log");
            Optional<Block> b = ok(mine);
            if (b.isPresent()) return b.get();
            b = ok(rest.startsWith("stripped") ? rest.replace("stripped_", "stripped_oak_") : "oak_" + rest);
            if (b.isPresent()) return b.get();
            b = ok(rest.equals("planks") ? "oak_planks" : rest.contains("log") ? "oak_log" : "oak_planks");
            if (b.isPresent() && isSolid(state)) return b.get();
            return Blocks.AIR;
        }
        if (path.equals("stripped_oak_wood")) return ok("stripped_oak_log").orElse(Blocks.OAK_LOG);
        if (path.equals("oak_wood")) return Blocks.OAK_LOG;
        // Fancy stones: their shape in cobblestone (stone for a full block).
        for (String stone : FANCY_STONES) {
            if (!path.startsWith(stone)) continue;
            String rest = path.substring(stone.length());
            if (rest.startsWith("s_")) rest = rest.substring(1);            // "stone_bricks_..." -> "_..."
            else if (rest.equals("s")) rest = "";
            Optional<Block> b = switch (rest) {
                case "_stairs" -> ok("cobblestone_stairs");
                case "_slab" -> ok("cobblestone_slab").or(() -> ok("smooth_stone_slab"));
                case "_wall" -> ok("cobblestone_wall");
                case "" -> ok("stone").or(() -> ok("cobblestone"));
                default -> Optional.empty();
            };
            if (b.isPresent()) return b.get();
        }
        // Coloured versions of something Unbeta has plain (glazed or otherwise).
        for (net.minecraft.util.DyeColor color : net.minecraft.util.DyeColor.values()) {
            String prefix = color.getName() + "_";
            if (path.startsWith(prefix)) {
                Optional<Block> b = ok(path.substring(prefix.length()));
                if (b.isPresent()) return b.get();
            }
        }
        return isSolid(state) ? Blocks.COBBLESTONE : Blocks.AIR;
    }

    private static boolean isSolid(BlockState state) {
        try {
            return state.isOpaque() && state.isFullCube(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
        } catch (Exception e) {
            return false;
        }
    }

    /** The new block, standing and facing as the old one did (every setting they share). */
    private static BlockState carry(BlockState from, BlockState to) {
        for (Property<?> p : from.getProperties()) to = copy(from, to, p);
        return to;
    }

    private static <T extends Comparable<T>> BlockState copy(BlockState from, BlockState to, Property<T> p) {
        for (Property<?> q : to.getProperties()) {
            if (!q.getName().equals(p.getName())) continue;
            Optional<?> v = q.parse(p.name(from.get(p)));
            if (v.isPresent()) return with(to, q, v.get());
        }
        return to;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState with(BlockState s, Property<T> p, Object v) {
        return s.with(p, (T) v);
    }
}
