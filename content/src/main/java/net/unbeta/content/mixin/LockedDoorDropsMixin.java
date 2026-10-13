package net.unbeta.content.mixin;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.unbeta.content.lockey.LockeyDoors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Blocks in a locked door's zone can't be destroyed, so nothing may drop their items either -
 * otherwise water washing at a torch, say, would hand out a free torch every time it tried.
 */
@Mixin(Block.class)
public abstract class LockedDoorDropsMixin {

    @Inject(method = "dropStacks(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)V",
            at = @At("HEAD"), cancellable = true)
    private static void unbeta_noDrops1(BlockState state, World world, BlockPos pos, CallbackInfo ci) {
        if (world instanceof ServerWorld sw && LockeyDoors.isProtected(sw, pos)) ci.cancel();
    }

    @Inject(method = "dropStacks(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/WorldAccess;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/entity/BlockEntity;)V",
            at = @At("HEAD"), cancellable = true)
    private static void unbeta_noDrops2(BlockState state, WorldAccess world, BlockPos pos, BlockEntity be, CallbackInfo ci) {
        if (world instanceof ServerWorld sw && LockeyDoors.isProtected(sw, pos)) ci.cancel();
    }

    @Inject(method = "dropStacks(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/entity/BlockEntity;Lnet/minecraft/entity/Entity;Lnet/minecraft/item/ItemStack;)V",
            at = @At("HEAD"), cancellable = true)
    private static void unbeta_noDrops3(BlockState state, World world, BlockPos pos, BlockEntity be, Entity entity,
                                        ItemStack tool, CallbackInfo ci) {
        if (world instanceof ServerWorld sw && LockeyDoors.isProtected(sw, pos)) ci.cancel();
    }
}
