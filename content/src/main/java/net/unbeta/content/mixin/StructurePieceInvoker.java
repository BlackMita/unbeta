package net.unbeta.content.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.structure.StructurePiece;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.StructureWorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets our structure mixins place blocks in a piece's own (rotated, mirrored) coordinates. */
@Mixin(StructurePiece.class)
public interface StructurePieceInvoker {

    @Invoker("addBlock")
    void unbeta_addBlock(StructureWorldAccess world, BlockState block, int x, int y, int z, BlockBox box);

    @Invoker("offsetPos")
    BlockPos.Mutable unbeta_offsetPos(int x, int y, int z);
}
