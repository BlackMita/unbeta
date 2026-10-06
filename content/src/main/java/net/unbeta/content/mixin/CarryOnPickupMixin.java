package net.unbeta.content.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.unbeta.content.lockey.CarryOnCompat;
import net.unbeta.content.lockey.LockeyState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Carry On just captured a chest. If it's locked, tuck the lock into the carried data so
 * it's rebuilt with the chest, and clear the spot left behind (otherwise the next chest
 * placed there would be born locked). Optional: inert if Carry On is absent.
 */
@Pseudo
@Mixin(targets = "tschipp.carryon.common.carry.CarryOnData", remap = false)
public abstract class CarryOnPickupMixin {

    @Shadow(remap = false) private NbtCompound nbt;

    @Inject(method = "setBlock", at = @At("TAIL"), remap = false, require = 0)
    private void unbeta_carryLock(BlockState state, BlockEntity be, CallbackInfo ci) {
        if (!(be instanceof ChestBlockEntity) || !(be.getWorld() instanceof ServerWorld sw)) return;
        UUID owner = LockeyState.lockedBy(sw, be.getPos());
        if (owner == null) return;
        this.nbt.getCompound("tile").putUuid(CarryOnCompat.LOCK_KEY, owner);
        LockeyState.unlock(sw, be.getPos());
    }
}
