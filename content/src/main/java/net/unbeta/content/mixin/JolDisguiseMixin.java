package net.unbeta.content.mixin;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.unbeta.content.jackolantern.JackOLanternRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Wearing an unlit Unbeta Jack o'Lantern acts as a disguise against all
 * hostile mobs except endermen. Mobs that have already aggroed will lose
 * their target each tick while the player wears the disguise.
 */
@Mixin(net.minecraft.entity.mob.MobEntity.class)
public abstract class JolDisguiseMixin {

    @Inject(method = "mobTick", at = @At("HEAD"))
    private void unbeta_jolDisguise(CallbackInfo ci) {
        net.minecraft.entity.mob.MobEntity self = (net.minecraft.entity.mob.MobEntity)(Object)this;
        if (!(self instanceof HostileEntity)) return;
        if (self.getWorld().isClient) return;
        if (self instanceof EndermanEntity) return; // endermen see through JoL
        if (!(self.getTarget() instanceof PlayerEntity player)) return;

        boolean wearingJol = player.getEquippedStack(EquipmentSlot.HEAD)
                .isOf(JackOLanternRegistry.UNLIT_ITEM);
        boolean playerAttackedUs = self.getAttacker() == player;

        if (wearingJol && !playerAttackedUs) {
            self.setTarget(null);
        }
    }
}
