package net.unbeta.content.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The End Gate's hollow is filled: 3x3 stone bricks at frame height (piece coordinates
 * x 4-6, y 3, z 9-11 - where vanilla puts the portal), with an oak sign on the centre
 * brick facing the room's entrance. New strongholds only.
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
        // The End Gate itself becomes chiseled stone bricks - the 12-block ring around the hole.
        // (Stonework, so Unmasons rebuild any that get broken, as chiseled.)
        BlockState chiseled = Blocks.CHISELED_STONE_BRICKS.getDefaultState();
        for (int i = 4; i <= 6; i++) {
            piece.unbeta_addBlock(world, chiseled, i, 3, 8, chunkBox);
            piece.unbeta_addBlock(world, chiseled, i, 3, 12, chunkBox);
        }
        for (int i = 9; i <= 11; i++) {
            piece.unbeta_addBlock(world, chiseled, 3, 3, i, chunkBox);
            piece.unbeta_addBlock(world, chiseled, 7, 3, i, chunkBox);
        }
        // Rotation 0: the sign's front faces the room's entrance; the piece rotates it with the room.
        piece.unbeta_addBlock(world, Blocks.OAK_SIGN.getDefaultState().with(Properties.ROTATION, 0), 5, 4, 10, chunkBox);
        BlockPos signPos = piece.unbeta_offsetPos(5, 4, 10);
        // During world generation a block entity has no world yet, so setText (which tells
        // the world to redraw) crashes. Load the text the way a saved sign loads: from NBT.
        if (chunkBox.contains(signPos) && world.getBlockEntity(signPos) instanceof SignBlockEntity sign) {
            try {
                SignText text = new SignText()
                        .withMessage(1, Text.literal("Best Chest"))
                        .withMessage(2, Text.literal("Here"));
                NbtCompound nbt = new NbtCompound();
                SignText.CODEC.encodeStart(NbtOps.INSTANCE, text).result()
                        .ifPresent(encoded -> nbt.put("front_text", encoded));
                sign.readNbt(nbt);
            } catch (Throwable t) {
                // A blank sign is fine; a crash during world generation is not.
            }
        }
    }
}
