package net.unbeta.content.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.structure.StructurePiece;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.ServerWorldAccess;
import net.unbeta.content.mimic.MimicChests;
import net.unbeta.content.stronghold.StrongholdVault;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every structure loot chest: maybe it's a mimic (which swallows the stronghold key if
 * this was the key chest); if not, and it's the stronghold's chosen chest, the key goes in.
 */
@Mixin(StructurePiece.class)
public abstract class StrongholdKeyChestMixin {

    @Inject(method = "addChest(Lnet/minecraft/world/ServerWorldAccess;Lnet/minecraft/util/math/BlockBox;Lnet/minecraft/util/math/random/Random;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/Identifier;Lnet/minecraft/block/BlockState;)Z",
            at = @At("RETURN"))
    private void unbeta_lootChest(ServerWorldAccess world, BlockBox box, Random random, BlockPos pos,
                                  Identifier lootTableId, BlockState block, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return;
        if (MimicChests.tryReplace(world, pos)) return;
        StrongholdVault.onChestPlaced(world, pos, random);
    }
}
