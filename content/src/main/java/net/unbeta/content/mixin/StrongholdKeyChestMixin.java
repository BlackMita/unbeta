package net.unbeta.content.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.structure.StructurePiece;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.ServerWorldAccess;
import net.unbeta.content.stronghold.StrongholdVault;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** When the stronghold's chosen chest is placed, slip the vault's key into it. */
@Mixin(StructurePiece.class)
public abstract class StrongholdKeyChestMixin {

    @Inject(method = "addChest(Lnet/minecraft/world/ServerWorldAccess;Lnet/minecraft/util/math/BlockBox;Lnet/minecraft/util/math/random/Random;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/Identifier;Lnet/minecraft/block/BlockState;)Z",
            at = @At("RETURN"))
    private void unbeta_keyChest(ServerWorldAccess world, BlockBox box, Random random, BlockPos pos,
                                 Identifier lootTableId, BlockState block, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) StrongholdVault.onChestPlaced(world, pos, random);
    }
}
