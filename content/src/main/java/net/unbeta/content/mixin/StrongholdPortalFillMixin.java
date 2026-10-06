package net.unbeta.content.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.unbeta.content.burntchest.BurntChests;
import net.unbeta.content.stronghold.StrongholdVault;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The End Gate is gone: its hollow is filled with 3x3 stone bricks (piece coordinates
 * x 4-6, y 3, z 9-11), its 12-block ring is chiseled stone bricks (stonework, so Unmasons
 * rebuild it), and the stronghold's vault - a Burnt Chest - sits on the centre, its front
 * to the room's entrance. New strongholds only.
 */
@Mixin(net.minecraft.structure.StrongholdGenerator.PortalRoom.class)
public abstract class StrongholdPortalFillMixin {

    @Inject(method = "generate", at = @At("TAIL"))
    private void unbeta_fillGate(StructureWorldAccess world, StructureAccessor structureAccessor,
                                 ChunkGenerator chunkGenerator, Random random, BlockBox chunkBox,
                                 ChunkPos chunkPos, BlockPos pivot, CallbackInfo ci) {
        StructurePieceInvoker piece = (StructurePieceInvoker)(Object)this;
        BlockState bricks = Blocks.STONE_BRICKS.getDefaultState();
        for (int x = 4; x <= 6; x++) {
            for (int z = 9; z <= 11; z++) {
                piece.unbeta_addBlock(world, bricks, x, 3, z, chunkBox);
            }
        }
        BlockState chiseled = Blocks.CHISELED_STONE_BRICKS.getDefaultState();
        for (int i = 4; i <= 6; i++) {
            piece.unbeta_addBlock(world, chiseled, i, 3, 8, chunkBox);
            piece.unbeta_addBlock(world, chiseled, i, 3, 12, chunkBox);
        }
        for (int i = 9; i <= 11; i++) {
            piece.unbeta_addBlock(world, chiseled, 3, 3, i, chunkBox);
            piece.unbeta_addBlock(world, chiseled, 7, 3, i, chunkBox);
        }
        // SOUTH in piece space faces the entrance (the same way the sign's rotation 0 did).
        piece.unbeta_addBlock(world, BurntChests.BLOCK.getDefaultState().with(ChestBlock.FACING, Direction.SOUTH),
                5, 4, 10, chunkBox);
        BlockPos vault = piece.unbeta_offsetPos(5, 4, 10).toImmutable();
        if (chunkBox.contains(vault)) StrongholdVault.onVaultPlaced(world, vault, random);
    }
}
