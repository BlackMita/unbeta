package net.unbeta.content.darkhold;

import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.EndPortalFrameBlock;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.structure.SimpleStructurePiece;
import net.minecraft.structure.StructureContext;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.structure.processor.BlockIgnoreStructureProcessor;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.StructureWorldAccess;
import net.unbeta.content.burntchest.BurntChests;
import net.unbeta.content.stronghold.StrongholdVault;

import java.util.ArrayList;
import java.util.List;

/**
 * One Darkhold room, placed from a template in structures/darkhold/. Its Data-mode structure
 * blocks are read as markers:
 * <ul>
 *   <li>{@code challenge} - a Guardian Post or an Endermite spawner (see DarkholdChallenges)</li>
 *   <li>{@code chest <facing>} - a treasury chest (facing is optional, default south)</li>
 *   <li>{@code endgate} - the End Gate platform with the locked vault on top</li>
 * </ul>
 */
public final class DarkholdRoomPiece extends SimpleStructurePiece {

    public static final Identifier TREASURY_LOOT = new Identifier("unbeta-content", "chests/darkhold_treasury");

    public DarkholdRoomPiece(StructureTemplateManager manager, String template, BlockPos pos, BlockRotation rotation) {
        super(Darkhold.ROOM, 0, manager, id(template), id(template).toString(), placement(rotation), pos);
    }

    public DarkholdRoomPiece(StructureTemplateManager manager, NbtCompound nbt) {
        super(Darkhold.ROOM, nbt, manager, id -> placement(BlockRotation.valueOf(nbt.getString("Rot"))));
    }

    static Identifier id(String template) {
        return new Identifier("unbeta-content", "darkhold/" + template);
    }

    static StructurePlacementData placement(BlockRotation rotation) {
        return new StructurePlacementData().setRotation(rotation).setMirror(BlockMirror.NONE)
                .addProcessor(BlockIgnoreStructureProcessor.IGNORE_STRUCTURE_BLOCKS);
    }

    /** Where to put a template's origin so that, turned by rotation, its footprint starts at min. */
    static BlockPos anchor(StructureTemplateManager manager, String template, BlockRotation rotation, BlockPos min) {
        StructureTemplate t = manager.getTemplateOrBlank(id(template));
        BlockBox box = t.calculateBoundingBox(placement(rotation), BlockPos.ORIGIN);
        return min.add(-box.getMinX(), -box.getMinY(), -box.getMinZ());
    }

    @Override
    protected void writeNbt(StructureContext context, NbtCompound nbt) {
        super.writeNbt(context, nbt);
        nbt.putString("Rot", this.placementData.getRotation().name());
    }

    /** World positions of this room's markers whose text starts with the given word. */
    public List<BlockPos> markers(String word) {
        List<BlockPos> out = new ArrayList<>();
        for (StructureTemplate.StructureBlockInfo info
                : this.template.getInfosForBlock(this.pos, this.placementData, Blocks.STRUCTURE_BLOCK)) {
            if (info.nbt() == null || !"DATA".equals(info.nbt().getString("mode"))) continue;
            String meta = info.nbt().getString("metadata").trim();
            if (meta.equals(word) || meta.startsWith(word + " ")) out.add(info.pos().toImmutable());
        }
        return out;
    }

    @Override
    protected void handleMetadata(String metadata, BlockPos pos, ServerWorldAccess world, Random random, BlockBox box) {
        String[] words = metadata.trim().split("\\s+");
        switch (words[0]) {
            case "challenge" -> DarkholdChallenges.place(world, pos, box);
            case "chest" -> {
                if (!box.contains(pos)) return;
                Direction facing = words.length > 1 && Direction.byName(words[1]) != null
                        ? Direction.byName(words[1]) : Direction.SOUTH;
                facing = this.placementData.getRotation().rotate(facing);
                world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2);
                this.addChest(world, box, random, pos, TREASURY_LOOT,
                        Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, facing));
            }
            case "endgate" -> {
                if (world instanceof StructureWorldAccess sw) endGate(sw, pos, random, box);
            }
            default -> { }
        }
    }

    /** The vault's position for an End Gate marker at pos: on the obsidian block in the ring's middle. */
    public static BlockPos vaultAt(BlockPos marker) {
        return marker.up(2);
    }

    /** A 7 x 7 platform, the End Gate frame ring around an obsidian heart, the vault chest on top. */
    private static void endGate(StructureWorldAccess world, BlockPos c, Random random, BlockBox box) {
        int x = c.getX(), y = c.getY(), z = c.getZ();
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++) DarkholdBlocks.put(world, box, x + dx, y, z + dz, DarkholdBlocks.OBSIDIAN);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean ringX = Math.abs(dx) == 2, ringZ = Math.abs(dz) == 2;
                if (ringX && ringZ) continue;
                if (ringX || ringZ) {
                    Direction in = dz == -2 ? Direction.SOUTH : dz == 2 ? Direction.NORTH
                                 : dx == -2 ? Direction.EAST : Direction.WEST;
                    DarkholdBlocks.put(world, box, x + dx, y + 1, z + dz,
                            Blocks.END_PORTAL_FRAME.getDefaultState().with(EndPortalFrameBlock.FACING, in));
                } else {
                    DarkholdBlocks.put(world, box, x + dx, y + 1, z + dz, DarkholdBlocks.OBSIDIAN);
                }
            }
        }
        BlockPos vault = vaultAt(c);
        if (box.contains(vault)) {
            world.setBlockState(vault, BurntChests.BLOCK.getDefaultState().with(ChestBlock.FACING, Direction.SOUTH), 2);
            StrongholdVault.onVaultPlaced(world, vault, random);
        }
    }
}
