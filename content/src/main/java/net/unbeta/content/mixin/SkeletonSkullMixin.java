package net.unbeta.content.mixin;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractSkeletonEntity.class)
public abstract class SkeletonSkullMixin {

    @Inject(method = "tickMovement", at = @At("HEAD"))
    private void unbeta_skullDisguise(CallbackInfo ci) {
        AbstractSkeletonEntity self = (AbstractSkeletonEntity)(Object)this;
        if (self.getWorld().isClient) return;
        if (!(self.getTarget() instanceof PlayerEntity player)) return;
        boolean wearingSkull = player.getEquippedStack(EquipmentSlot.HEAD)
                .isOf(Items.SKELETON_SKULL);
        boolean playerAttackedUs = self.getAttacker() == player;
        if (wearingSkull && !playerAttackedUs) {
            self.setTarget(null);
        }
    }
}
