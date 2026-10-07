package net.unbeta.content.skyhold;

import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.structure.StructureContext;
import net.minecraft.structure.StructurePiece;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;

/**
 * One loot chest in a Skyhold room. Placed through vanilla's structure-chest path, so it is
 * treated exactly like a stone stronghold's chests: it may become a mimic, and it may be the
 * chest that holds the vault's key.
 */
public final class SkyholdChestPiece extends StructurePiece {

    private final BlockPos pos;
    private final Direction facing;
    private final Identifier loot;

    public SkyholdChestPiece(BlockPos pos, Direction facing, Identifier loot) {
        super(Skyhold.CHEST, 0, new BlockBox(pos));
        this.pos = pos.toImmutable();
        this.facing = facing;
        this.loot = loot;
    }

    public SkyholdChestPiece(NbtCompound nbt) {
        super(Skyhold.CHEST, nbt);
        this.pos = BlockPos.fromLong(nbt.getLong("Pos"));
        this.facing = Direction.byId(nbt.getInt("Facing"));
        this.loot = new Identifier(nbt.getString("Loot"));
    }

    public BlockPos pos() {
        return pos;
    }

    @Override
    protected void writeNbt(StructureContext context, NbtCompound nbt) {
        nbt.putLong("Pos", pos.asLong());
        nbt.putInt("Facing", facing.getId());
        nbt.putString("Loot", loot.toString());
    }

    @Override
    public void generate(StructureWorldAccess world, StructureAccessor structures, ChunkGenerator generator,
                         Random random, BlockBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
        this.addChest(world, chunkBox, random, pos, loot, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, facing));
    }
}
