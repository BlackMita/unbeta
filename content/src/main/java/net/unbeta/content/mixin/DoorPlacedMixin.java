package net.unbeta.content.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.unbeta.content.lockey.LockeyDoors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A placed door starts clean (no leftover lock from an old door here) and keeps its item's damage. */
@Mixin(DoorBlock.class)
public abstract class DoorPlacedMixin {

    @Inject(method = "onPlaced", at = @At("TAIL"))
    private void unbeta_placedDoor(World world, BlockPos pos, BlockState state, LivingEntity placer,
                                   ItemStack stack, CallbackInfo ci) {
        if (world instanceof ServerWorld sw) LockeyDoors.onDoorPlaced(sw, LockeyDoors.lowerHalf(pos, state), stack);
    }
}
