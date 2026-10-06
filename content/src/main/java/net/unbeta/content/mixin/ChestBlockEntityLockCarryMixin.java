package net.unbeta.content.mixin;

import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.unbeta.content.lockey.CarryOnCompat;
import net.unbeta.content.lockey.LockeyCarrier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * A chest rebuilt from Carry On's data remembers the lock it carried until it joins a world.
 * Ordinary saves never contain LOCK_KEY - only carried data does - so loading a chunk can't
 * trigger this.
 */
@Mixin(ChestBlockEntity.class)
public abstract class ChestBlockEntityLockCarryMixin implements LockeyCarrier {

    @Unique private UUID unbeta_pendingLock;

    @Inject(method = "readNbt", at = @At("TAIL"))
    private void unbeta_readCarriedLock(NbtCompound nbt, CallbackInfo ci) {
        if (nbt.containsUuid(CarryOnCompat.LOCK_KEY)) this.unbeta_pendingLock = nbt.getUuid(CarryOnCompat.LOCK_KEY);
    }

    @Override
    public UUID unbeta_takePendingLock() {
        UUID id = this.unbeta_pendingLock;
        this.unbeta_pendingLock = null;
        return id;
    }
}
