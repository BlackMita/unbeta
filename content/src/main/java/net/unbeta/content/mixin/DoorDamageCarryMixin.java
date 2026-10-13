package net.unbeta.content.mixin;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.unbeta.content.lockey.LockeyDoors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * A door's damage travels with it: stamped onto the door item it drops, and forgotten (along
 * with any lock) once the door block itself is gone.
 */
@Mixin(AbstractBlock.class)
public abstract class DoorDamageCarryMixin {

    @Inject(method = "getDroppedStacks", at = @At("RETURN"))
    private void unbeta_stampDoorDamage(BlockState state, LootContextParameterSet.Builder builder,
                                        CallbackInfoReturnable<List<ItemStack>> cir) {
        if (!(state.getBlock() instanceof DoorBlock) || state.get(DoorBlock.HALF) != DoubleBlockHalf.LOWER) return;
        Vec3d origin = builder.getOptional(LootContextParameters.ORIGIN);
        if (origin == null) return;
        ServerWorld world = builder.getWorld();
        LockeyDoors.stampDrops(world, BlockPos.ofFloored(origin), state.getBlock(), cir.getReturnValue());
    }

    @Inject(method = "onStateReplaced", at = @At("HEAD"))
    private void unbeta_doorGone(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved,
                                 CallbackInfo ci) {
        if (state.getBlock() instanceof DoorBlock && !newState.isOf(state.getBlock()) && world instanceof ServerWorld sw) {
            LockeyDoors.onDoorGone(sw, LockeyDoors.lowerHalf(pos, state));
        }
    }
}
