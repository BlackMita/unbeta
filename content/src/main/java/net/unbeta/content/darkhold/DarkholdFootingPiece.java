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

/** Obsidian filled in under a ground-floor room wherever the ground dips away beneath it. */
public final class DarkholdFootingPiece extends StructurePiece {

    private final int minX, minZ, maxX, maxZ, floorY;

    public DarkholdFootingPiece(int minX, int minZ, int maxX, int maxZ, int floorY) {
        super(Darkhold.FOOTING, 0, new BlockBox(minX, floorY - 40, minZ, maxX, floorY - 1, maxZ));
        this.minX = minX;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxZ = maxZ;
        this.floorY = floorY;
    }

    public DarkholdFootingPiece(NbtCompound nbt) {
        super(Darkhold.FOOTING, nbt);
        this.minX = nbt.getInt("MinX");
        this.minZ = nbt.getInt("MinZ");
        this.maxX = nbt.getInt("MaxX");
        this.maxZ = nbt.getInt("MaxZ");
        this.floorY = nbt.getInt("FloorY");
    }

    @Override
    protected void writeNbt(StructureContext context, NbtCompound nbt) {
        nbt.putInt("MinX", minX);
        nbt.putInt("MinZ", minZ);
        nbt.putInt("MaxX", maxX);
        nbt.putInt("MaxZ", maxZ);
        nbt.putInt("FloorY", floorY);
    }

    @Override
    public void generate(StructureWorldAccess world, StructureAccessor structures, ChunkGenerator generator,
                         Random random, BlockBox box, ChunkPos chunkPos, BlockPos pivot) {
        for (int x = minX; x <= maxX; x++)
            for (int z = minZ; z <= maxZ; z++) DarkholdBlocks.foot(world, box, x, floorY - 1, z);
    }
}
