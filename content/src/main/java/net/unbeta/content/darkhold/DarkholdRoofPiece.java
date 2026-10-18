package net.unbeta.content.darkhold;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.structure.StructureContext;
import net.minecraft.structure.StructurePiece;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.unbeta.content.UnbetaContent;
import net.unbeta.content.block.FireColor;
import net.unbeta.content.block.ObsidianFireBlock;

/** A stepped obsidian pyramid on top of an uppermost cell, usually with one obsidian fire burning on its point. */
public final class DarkholdRoofPiece extends StructurePiece {

    private final int x0, y0, z0;
    private final boolean fire;

    /** (x0, z0): the cell's corner; y0: the first layer, just above the cell's ceiling. */
    public DarkholdRoofPiece(int x0, int y0, int z0, boolean fire) {
        super(Darkhold.ROOF, 0, new BlockBox(x0, y0, z0, x0 + 10, y0 + 6, z0 + 10));
        this.x0 = x0;
        this.y0 = y0;
        this.z0 = z0;
        this.fire = fire;
    }

    public DarkholdRoofPiece(NbtCompound nbt) {
        super(Darkhold.ROOF, nbt);
        this.x0 = nbt.getInt("X");
        this.y0 = nbt.getInt("Y");
        this.z0 = nbt.getInt("Z");
        this.fire = !nbt.contains("Fire") || nbt.getBoolean("Fire");
    }

    @Override
    protected void writeNbt(StructureContext context, NbtCompound nbt) {
        nbt.putInt("X", x0);
        nbt.putInt("Y", y0);
        nbt.putInt("Z", z0);
        nbt.putBoolean("Fire", fire);
    }

    @Override
    public void generate(StructureWorldAccess world, StructureAccessor structures, ChunkGenerator generator,
                         Random random, BlockBox box, ChunkPos chunkPos, BlockPos pivot) {
        for (int k = 0; k <= 5; k++) {
            for (int x = k; x <= 10 - k; x++)
                for (int z = k; z <= 10 - k; z++)
                    DarkholdBlocks.put(world, box, x0 + x, y0 + k, z0 + z, DarkholdBlocks.OBSIDIAN);
        }
        if (fire && UnbetaContent.OBSIDIAN_FIRE != null) {
            BlockPos top = new BlockPos(x0 + 5, y0 + 6, z0 + 5);
            FireColor color = FireColor.VALUES[(int) Math.floorMod(DarkholdBlocks.mix(world.getSeed(), top),
                    (long) FireColor.VALUES.length)];
            DarkholdBlocks.put(world, box, top.getX(), top.getY(), top.getZ(),
                    UnbetaContent.OBSIDIAN_FIRE.getDefaultState().with(ObsidianFireBlock.COLOR, color));
        }
    }
}
