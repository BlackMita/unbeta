package net.unbeta.content.mixin;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.unbeta.content.lockey.LockeyCarrier;
import net.unbeta.content.lockey.LockeyState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/** The moment a carried chest is set down into a world, it locks again - to the same key. */
@Mixin(BlockEntity.class)
public abstract class BlockEntityCarriedLockMixin {

    @Inject(method = "setWorld", at = @At("TAIL"))
    private void unbeta_applyCarriedLock(World world, CallbackInfo ci) {
        if (!((Object) this instanceof LockeyCarrier carrier) || !(world instanceof ServerWorld sw)) return;
        UUID id = carrier.unbeta_takePendingLock();
        if (id != null) LockeyState.lockChest(sw, ((BlockEntity) (Object) this).getPos(), id);
    }
}
