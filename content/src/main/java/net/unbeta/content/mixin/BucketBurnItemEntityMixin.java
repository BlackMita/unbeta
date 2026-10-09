package net.unbeta.content.mixin;

import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.unbeta.content.bucket.BucketBurn;
import net.unbeta.content.bucket.IceFreeze;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A dropped lava bucket keeps burning and spills where it lies; a dropped Ice Bucket freezes where it lies. */
@Mixin(ItemEntity.class)
public abstract class BucketBurnItemEntityMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void unbeta_burnOnGround(CallbackInfo ci) {
        ItemEntity self = (ItemEntity)(Object)this;
        if (self.getWorld().isClient) return;
        ItemStack stack = self.getStack();
        if (IceFreeze.isFilled(stack)) {
            if (!IceFreeze.freezing(stack)) IceFreeze.start(stack, self.getWorld());
            else if (IceFreeze.due(stack, self.getWorld())) {
                IceFreeze.sound((ServerWorld) self.getWorld(), self.getBlockPos(), stack);
                self.setStack(IceFreeze.result(stack));
            }
            return;
        }
        if (!BucketBurn.due(stack, self.getWorld())) return;
        BucketBurn.burnOut((ServerWorld) self.getWorld(), stack, self.getBlockPos());
        self.discard();
        ci.cancel();
    }
}
