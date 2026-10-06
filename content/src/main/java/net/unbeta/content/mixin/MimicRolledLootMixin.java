package net.unbeta.content.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.unbeta.content.mimic.MimicAccess;
import net.unbeta.content.mimic.MimicLocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A mimic whose loot was rolled into it when locked doesn't roll its loot table again on death. */
@Mixin(LivingEntity.class)
public abstract class MimicRolledLootMixin {

    @Inject(method = "dropLoot", at = @At("HEAD"), cancellable = true)
    private void unbeta_rolledAlready(DamageSource source, boolean causedByPlayer, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof MimicAccess && self.getCommandTags().contains(MimicLocks.ROLLED)
                && !MimicLocks.capturing()) {
            ci.cancel();
        }
    }
}
