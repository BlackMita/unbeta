package net.unbeta.content.dye;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.type.WoodTypeBuilder;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSetType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.PillarBlock;
import net.minecraft.block.PressurePlateBlock;
import net.minecraft.block.SignBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.WallSignBlock;
import net.minecraft.block.WoodType;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.SignItem;
import net.minecraft.item.TallBlockItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.state.property.Property;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Colour for Unbeta: every wood piece (planks, stairs, slab, fence, fence gate, door, trapdoor,
 * button, pressure plate, sign) and plain Stone come in the 16 dye colours.
 * Ids: {@code <colour>_wood_planks}, {@code <colour>_wood_stairs} ... {@code <colour>_wood_sign},
 * {@code <colour>_stone}. (No dyed ladders: they would fight the Smart Ladders mod.) Each behaves exactly like its plain (oak / stone) block, and drops
 * the plain block. Their models and textures are made from vanilla's by
 * tools/make_dyed_blocks.py.
 */
public final class DyedBlocks {

    /** A family of blocks: the plain one and its 16 dyed versions. */
    public enum Shape {
        PLANKS("wood_planks"), STAIRS("wood_stairs"), SLAB("wood_slab"), FENCE("wood_fence"),
        FENCE_GATE("wood_fence_gate"), DOOR("wood_door"), TRAPDOOR("wood_trapdoor"), BUTTON("wood_button"),
        PRESSURE_PLATE("wood_pressure_plate"), SIGN("wood_sign"), WALL_SIGN("wood_wall_sign"),
        STRIPPED_LOG("stripped_log"), STONE("stone");

        public final String suffix;

        Shape(String suffix) {
            this.suffix = suffix;
        }
    }

    /** Which family a block belongs to, and its colour (null = the plain block). */
    public record Entry(Shape shape, DyeColor color) {}

    public static final Map<Shape, Block> PLAIN = new EnumMap<>(Shape.class);
    public static final Map<Shape, EnumMap<DyeColor, Block>> DYED = new EnumMap<>(Shape.class);
    public static final Map<DyeColor, WoodType> WOOD_TYPES = new EnumMap<>(DyeColor.class);
    private static final Map<Block, Entry> LOOKUP = new HashMap<>();

    /** Blocks drawn with see-through pixels (doors, trapdoors). */
    public static final List<Block> CUTOUT = new ArrayList<>();

    private DyedBlocks() {}

    private static Identifier id(String path) {
        return new Identifier("unbeta-content", path);
    }

    public static Entry entry(Block block) {
        return LOOKUP.get(block);
    }

    public static Block block(Shape shape, DyeColor color) {
        return color == null ? PLAIN.get(shape) : DYED.get(shape).get(color);
    }

    public static void register() {
        PLAIN.put(Shape.PLANKS, Blocks.OAK_PLANKS);
        PLAIN.put(Shape.STAIRS, Blocks.OAK_STAIRS);
        PLAIN.put(Shape.SLAB, Blocks.OAK_SLAB);
        PLAIN.put(Shape.FENCE, Blocks.OAK_FENCE);
        PLAIN.put(Shape.FENCE_GATE, Blocks.OAK_FENCE_GATE);
        PLAIN.put(Shape.DOOR, Blocks.OAK_DOOR);
        PLAIN.put(Shape.TRAPDOOR, Blocks.OAK_TRAPDOOR);
        PLAIN.put(Shape.BUTTON, Blocks.OAK_BUTTON);
        PLAIN.put(Shape.PRESSURE_PLATE, Blocks.OAK_PRESSURE_PLATE);
        PLAIN.put(Shape.SIGN, Blocks.OAK_SIGN);
        PLAIN.put(Shape.WALL_SIGN, Blocks.OAK_WALL_SIGN);
        PLAIN.put(Shape.STRIPPED_LOG, Blocks.STRIPPED_OAK_LOG);
        PLAIN.put(Shape.STONE, Blocks.STONE);
        for (Shape s : Shape.values()) {
            DYED.put(s, new EnumMap<>(DyeColor.class));
            LOOKUP.put(PLAIN.get(s), new Entry(s, null));
        }
        // One set of dyed stripped logs for every kind: stripped birch and spruce paint too.
        LOOKUP.put(Blocks.STRIPPED_BIRCH_LOG, new Entry(Shape.STRIPPED_LOG, null));
        LOOKUP.put(Blocks.STRIPPED_SPRUCE_LOG, new Entry(Shape.STRIPPED_LOG, null));

        for (DyeColor c : DyeColor.values()) {
            String n = c.getName() + "_";
            WoodType wood = new WoodTypeBuilder().register(id(c.getName() + "_wood"), BlockSetType.OAK);
            WOOD_TYPES.put(c, wood);

            Block planks = add(Shape.PLANKS, c, n + "wood_planks",
                    new Block(AbstractBlock.Settings.copy(Blocks.OAK_PLANKS).mapColor(c)));
            add(Shape.STAIRS, c, n + "wood_stairs",
                    new StairsBlock(planks.getDefaultState(), AbstractBlock.Settings.copy(Blocks.OAK_STAIRS).mapColor(c)) {});
            add(Shape.SLAB, c, n + "wood_slab", new SlabBlock(AbstractBlock.Settings.copy(Blocks.OAK_SLAB).mapColor(c)));
            add(Shape.FENCE, c, n + "wood_fence", new FenceBlock(AbstractBlock.Settings.copy(Blocks.OAK_FENCE).mapColor(c)));
            add(Shape.FENCE_GATE, c, n + "wood_fence_gate",
                    new FenceGateBlock(AbstractBlock.Settings.copy(Blocks.OAK_FENCE_GATE).mapColor(c), WoodType.OAK));
            Block door = add(Shape.DOOR, c, n + "wood_door",
                    new DoorBlock(AbstractBlock.Settings.copy(Blocks.OAK_DOOR).mapColor(c), BlockSetType.OAK));
            Block trapdoor = add(Shape.TRAPDOOR, c, n + "wood_trapdoor",
                    new TrapdoorBlock(AbstractBlock.Settings.copy(Blocks.OAK_TRAPDOOR).mapColor(c), BlockSetType.OAK));
            add(Shape.BUTTON, c, n + "wood_button",
                    new ButtonBlock(AbstractBlock.Settings.copy(Blocks.OAK_BUTTON), BlockSetType.OAK, 30, true));
            add(Shape.PRESSURE_PLATE, c, n + "wood_pressure_plate",
                    new PressurePlateBlock(PressurePlateBlock.ActivationRule.EVERYTHING,
                            AbstractBlock.Settings.copy(Blocks.OAK_PRESSURE_PLATE).mapColor(c), BlockSetType.OAK));
            Block sign = add(Shape.SIGN, c, n + "wood_sign",
                    new SignBlock(AbstractBlock.Settings.copy(Blocks.OAK_SIGN).mapColor(c), wood));
            add(Shape.WALL_SIGN, c, n + "wood_wall_sign",
                    new WallSignBlock(AbstractBlock.Settings.copy(Blocks.OAK_WALL_SIGN).mapColor(c).dropsLike(sign), wood));
            add(Shape.STRIPPED_LOG, c, n + "stripped_log",
                    new PillarBlock(AbstractBlock.Settings.copy(Blocks.STRIPPED_OAK_LOG).mapColor(c)));
            add(Shape.STONE, c, n + "stone", new Block(AbstractBlock.Settings.copy(Blocks.STONE).mapColor(c)));
            CUTOUT.add(door);
            CUTOUT.add(trapdoor);

            // Items: one per block, except the wall sign (the sign item places both).
            for (Shape s : Shape.values()) {
                Block b = DYED.get(s).get(c);
                Identifier bid = Registries.BLOCK.getId(b);
                Item item = switch (s) {
                    case WALL_SIGN -> null;
                    case SIGN -> new SignItem(new Item.Settings().maxCount(16), b, DYED.get(Shape.WALL_SIGN).get(c));
                    case DOOR -> new TallBlockItem(b, new Item.Settings());
                    default -> new BlockItem(b, new Item.Settings());
                };
                if (item != null) Registry.register(Registries.ITEM, bid, item);
            }

            FlammableBlockRegistry fire = FlammableBlockRegistry.getDefaultInstance();
            for (Shape s : new Shape[]{Shape.PLANKS, Shape.STAIRS, Shape.SLAB, Shape.FENCE, Shape.FENCE_GATE})
                fire.add(DYED.get(s).get(c), 5, 20);
            fire.add(DYED.get(Shape.STRIPPED_LOG).get(c), 5, 5);
        }

        // Every dyed block lives in the Colored Blocks tab, shape by shape, colour by colour.
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COLORED_BLOCKS).register(entries -> {
            for (Shape s : Shape.values()) {
                if (s == Shape.WALL_SIGN) continue;
                for (DyeColor c : DyeColor.values()) entries.add(DYED.get(s).get(c));
            }
        });
    }

    private static Block add(Shape shape, DyeColor color, String path, Block block) {
        Registry.register(Registries.BLOCK, id(path), block);
        DYED.get(shape).put(color, block);
        LOOKUP.put(block, new Entry(shape, color));
        return block;
    }

    /** The same state on another block of the family: every shared property carried over. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static BlockState recolor(BlockState state, Block to) {
        BlockState out = to.getDefaultState();
        for (Property p : state.getProperties()) {
            if (out.contains(p)) out = out.with(p, state.get(p));
        }
        return out;
    }

    /**
     * Turns the block at pos into the given colour (null = plain), keeping its shape, facing and
     * so on - both halves of a door, and a sign's writing. False if it isn't dyeable or is
     * already that colour.
     */
    public static boolean paint(World world, BlockPos pos, DyeColor color) {
        BlockState state = world.getBlockState(pos);
        Entry e = entry(state.getBlock());
        if (e == null || e.color() == color) return false;
        Block to = block(e.shape(), color);
        if (to == null) return false;
        if (world.isClient) return true;

        BlockEntity be = world.getBlockEntity(pos);
        NbtCompound kept = be == null ? null : be.createNbt();
        int flags = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;
        world.setBlockState(pos, recolor(state, to), flags);
        if (state.getBlock() instanceof DoorBlock) {
            BlockPos other = state.get(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos.up() : pos.down();
            BlockState otherState = world.getBlockState(other);
            if (otherState.isOf(state.getBlock())) world.setBlockState(other, recolor(otherState, to), flags);
        }
        if (kept != null) {
            BlockEntity now = world.getBlockEntity(pos);
            if (now != null) {
                now.readNbt(kept);
                now.markDirty();
                world.updateListeners(pos, world.getBlockState(pos), world.getBlockState(pos), Block.NOTIFY_ALL);
            }
        }
        return true;
    }
}
