package net.unbeta.hg;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.SignBlock;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.enums.WallMountLocation;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.Heightmap;

import java.io.File;

/**
 * The spawn cage: about one chunk across, built at spawn on every new world. Nobody can break,
 * place in or change it (an operator in Creative can, to edit it), and explosions don't touch it.
 * Its fence gates stand open (the lobby is for wandering the map and planning). Any button
 * inside is a surrender button (it asks you to confirm, like /surrender).
 *
 * <p>My first draft is built in code. Edit it in Creative and run /hg cage save: from then on
 * every round builds yours (config/unbeta-hg-cage.nbt). /hg cage default goes back to the draft.
 */
public final class Cage {

    private Cage() {}

    public static final int SIZE = 16;
    public static final int HEIGHT = 12;
    /** The cage's corner (floor level, lowest x and z), and the space it protects. */
    private static BlockPos origin;
    private static BlockBox box;

    private static File file() {
        return FabricLoader.getInstance().getConfigDir().resolve("unbeta-hg-cage.nbt").toFile();
    }

    public static boolean protects(BlockPos pos) {
        return box != null && box.contains(pos);
    }

    public static BlockBox box() {
        return box;
    }

    // ------------------------------------------------------------------ building

    /** Builds the cage centred on spawn (your saved one if there is one), and puts spawn inside it. */
    public static void build(ServerWorld world, BlockPos spawn) {
        int floor = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ()) - 1;
        origin = new BlockPos(spawn.getX() - SIZE / 2, floor, spawn.getZ() - SIZE / 2);
        Vec3i size = new Vec3i(SIZE, HEIGHT, SIZE);
        // Clear the space, and give it a solid stone footing over caves or water.
        for (int x = 0; x < SIZE; x++) for (int z = 0; z < SIZE; z++) {
            for (int y = 1; y < HEIGHT; y++) world.setBlockState(origin.add(x, y, z), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            for (int y = -1; y >= -8; y--) {
                BlockPos p = origin.add(x, y, z);
                BlockState st = world.getBlockState(p);
                if (!st.isReplaceable() && st.getFluidState().isEmpty()) break;
                world.setBlockState(p, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            }
        }
        StructureTemplate saved = load();
        if (saved != null) {
            saved.place(world, origin, origin, new StructurePlacementData(), world.getRandom(), Block.NOTIFY_LISTENERS);
            size = saved.getSize();
            UnbetaHG.LOG.info("Built the saved cage ({}) at {}", size.toShortString(), origin.toShortString());
        } else {
            draft(world);
            UnbetaHG.LOG.info("Built the draft cage at {}", origin.toShortString());
        }
        box = BlockBox.create(origin, origin.add(size.getX() - 1, Math.max(HEIGHT, size.getY()) - 1, size.getZ() - 1));
        // Iron bars, fences and the like join up with their neighbours.
        for (BlockPos p : BlockPos.iterate(box.getMinX(), box.getMinY(), box.getMinZ(), box.getMaxX(), box.getMaxY(), box.getMaxZ())) {
            BlockState st = world.getBlockState(p);
            if (!st.isAir()) world.setBlockState(p, Block.postProcessState(st, world, p), Block.NOTIFY_LISTENERS);
        }
        setGates(world, true);   // open from the start: the lobby is for wandering and planning
        world.setSpawnPos(new BlockPos(spawn.getX(), floor + 1, spawn.getZ()), 0F);
    }

    /**
     * My draft: a stone-brick floor lit by four glowsand tiles; walls of iron bars between
     * stone-brick pillars, 4 high; a double fence gate in the middle of each side under a stone
     * lintel; and the surrender post: a button with a sign on top.
     */
    private static void draft(ServerWorld world) {
        BlockState brick = Blocks.STONE_BRICKS.getDefaultState();
        BlockState bars = Blocks.IRON_BARS.getDefaultState();
        Block glowsand = Registries.BLOCK.get(new Identifier("unbeta-content", "glowsand"));
        int last = SIZE - 1;
        for (int x = 0; x < SIZE; x++) for (int z = 0; z < SIZE; z++) {
            boolean lamp = (x == 4 || x == 11) && (z == 4 || z == 11);
            set(world, x, 0, z, lamp && glowsand != Blocks.AIR ? glowsand.getDefaultState() : brick);
            boolean edge = x == 0 || z == 0 || x == last || z == last;
            if (!edge) continue;
            boolean pillar = (x == 0 || x == last || x == 5 || x == 10) && (z == 0 || z == last || z == 5 || z == 10);
            boolean gate = (x == 7 || x == 8) && (z == 0 || z == last) || (z == 7 || z == 8) && (x == 0 || x == last);
            for (int y = 1; y <= 4; y++) {
                if (pillar) set(world, x, y, z, brick);
                else if (gate) {
                    if (y == 1) {
                        Direction facing = (z == 0 || z == last) ? Direction.NORTH : Direction.EAST;
                        set(world, x, y, z, Blocks.OAK_FENCE_GATE.getDefaultState().with(FenceGateBlock.FACING, facing));
                    } else if (y >= 3) set(world, x, y, z, brick);
                } else set(world, x, y, z, bars);
            }
            if (pillar) set(world, x, 5, z, Blocks.STONE_BRICK_SLAB.getDefaultState());
        }
        // The surrender post, in a corner: a pillar with a button on its face and a sign on top.
        set(world, 3, 1, 3, brick);
        set(world, 4, 1, 3, Blocks.STONE_BUTTON.getDefaultState().with(ButtonBlock.FACE, WallMountLocation.WALL).with(ButtonBlock.FACING, Direction.EAST));
        set(world, 3, 2, 3, Blocks.OAK_SIGN.getDefaultState().with(SignBlock.ROTATION, 12));
        if (world.getBlockEntity(origin.add(3, 2, 3)) instanceof SignBlockEntity sign) {
            sign.changeText(t -> t.withMessage(0, Text.literal("SURRENDER"))
                    .withMessage(1, Text.literal("press the button"))
                    .withMessage(2, Text.literal("to leave the round"))
                    .withMessage(3, Text.literal("(asks first)")), true);
        }
    }

    private static void set(ServerWorld world, int x, int y, int z, BlockState state) {
        world.setBlockState(origin.add(x, y, z), state, Block.NOTIFY_LISTENERS);
    }

    /** Opens or shuts every fence gate in the cage. */
    public static void setGates(ServerWorld world, boolean open) {
        if (box == null) return;
        boolean any = false;
        for (BlockPos p : BlockPos.iterate(box.getMinX(), box.getMinY(), box.getMinZ(), box.getMaxX(), box.getMaxY(), box.getMaxZ())) {
            BlockState st = world.getBlockState(p);
            if (st.getBlock() instanceof FenceGateBlock && st.get(FenceGateBlock.OPEN) != open) {
                world.setBlockState(p, st.with(FenceGateBlock.OPEN, open), Block.NOTIFY_ALL);
                any = true;
            }
        }
        if (any && open) {
            BlockPos c = box.getCenter();
            world.playSound(null, c, SoundEvents.BLOCK_FENCE_GATE_OPEN, SoundCategory.BLOCKS, 2F, 0.8F);
            world.playSound(null, c, SoundEvents.BLOCK_IRON_DOOR_OPEN, SoundCategory.BLOCKS, 2F, 0.6F);
        }
    }

    // ------------------------------------------------------------------ your template

    /** /hg cage save: the cage as it stands becomes the template every round builds. */
    public static String save(ServerWorld world) {
        if (box == null) return "No cage in this world.";
        StructureTemplate t = new StructureTemplate();
        Vec3i size = new Vec3i(box.getBlockCountX(), box.getBlockCountY(), box.getBlockCountZ());
        t.saveFromWorld(world, origin, size, false, Blocks.STRUCTURE_VOID);
        try {
            NbtIo.writeCompressed(t.writeNbt(new NbtCompound()), file());
            return "Saved the cage (" + size.toShortString() + "): every new round builds it now.";
        } catch (Exception e) {
            UnbetaHG.LOG.error("Couldn't save the cage", e);
            return "Couldn't save the cage: " + e.getMessage();
        }
    }

    /** /hg cage default: back to my draft. */
    public static String forget() {
        return file().delete() ? "Deleted your saved cage: new rounds build the draft." : "There was no saved cage.";
    }

    private static StructureTemplate load() {
        File f = file();
        if (!f.exists()) return null;
        try {
            StructureTemplate t = new StructureTemplate();
            t.readNbt(Registries.BLOCK.getReadOnlyWrapper(), NbtIo.readCompressed(f));
            return t;
        } catch (Exception e) {
            UnbetaHG.LOG.error("Couldn't read the saved cage - building the draft", e);
            return null;
        }
    }

    // ------------------------------------------------------------------ protection

    private static boolean exempt(PlayerEntity p) {
        return p.isCreative() && p.hasPermissionLevel(2);
    }

    public static void register() {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, be) -> exempt(player) || !protects(pos));
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient || exempt(player)) return ActionResult.PASS;
            BlockPos pos = hit.getBlockPos();
            BlockState st = world.getBlockState(pos);
            if (protects(pos) && st.getBlock() instanceof ButtonBlock) {
                Round.Phase ph = Round.phase();
                if ((ph == Round.Phase.GRACE || ph == Round.Phase.GAME) && player instanceof ServerPlayerEntity sp) {
                    HGCommands.surrenderPrompt(sp);
                } else if (player instanceof ServerPlayerEntity sp) {
                    sp.sendMessage(Text.literal("The surrender button works once the round is under way.").formatted(Formatting.GRAY));
                }
                return ActionResult.FAIL;
            }
            boolean placing = player.getStackInHand(hand).getItem() instanceof BlockItem;
            BlockPos target = st.isReplaceable() ? pos : pos.offset(hit.getSide());
            if (protects(pos) && !placing) return ActionResult.FAIL;          // no opening, shutting, changing
            if (placing && protects(target)) return ActionResult.FAIL;         // no building in it
            return ActionResult.PASS;
        });
        UseItemCallback.EVENT.register((player, world, hand) -> {
            // Buckets and the like, aimed at the cage.
            if (world.isClient || exempt(player)) return TypedActionResult.pass(player.getStackInHand(hand));
            HitResult hit = player.raycast(5.0, 0F, true);
            if (hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK
                    && (protects(bh.getBlockPos()) || protects(bh.getBlockPos().offset(bh.getSide())))) {
                return TypedActionResult.fail(player.getStackInHand(hand));
            }
            return TypedActionResult.pass(player.getStackInHand(hand));
        });
    }
}
