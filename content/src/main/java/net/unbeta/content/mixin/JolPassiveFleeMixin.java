package net.unbeta.content.mixin;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ai.goal.FleeEntityGoal;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.unbeta.content.jackolantern.JackOLanternRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.entity.mob.MobEntity.class)
public abstract class JolPassiveFleeMixin {

    private FleeEntityGoal<PlayerEntity> unbeta_jolFleeGoal = null;

    @Inject(method = "mobTick", at = @At("HEAD"))
    private void unbeta_jolPassiveFlee(CallbackInfo ci) {
        net.minecraft.entity.mob.MobEntity self = (net.minecraft.entity.mob.MobEntity)(Object)this;
        if (!(self.getWorld() instanceof net.minecraft.server.world.ServerWorld sw)) return;

        boolean isPassive = self instanceof AnimalEntity && !(self instanceof WolfEntity);
        boolean isVillager = self instanceof VillagerEntity;
        if (!isPassive && !isVillager) return;

        // Find nearest JoL-wearing non-spectator player using server list
        ServerPlayerEntity nearest = null;
        double nearestDist = 16.0 * 16.0;
        for (ServerPlayerEntity sp : sw.getPlayers()) {
            if (sp.isSpectator()) continue;
            if (!sp.getEquippedStack(EquipmentSlot.HEAD)
                    .isOf(JackOLanternRegistry.UNLIT_ITEM)) continue;
            double d = sp.squaredDistanceTo(self);
            if (d < nearestDist) { nearestDist = d; nearest = sp; }
        }

        boolean shouldFlee = nearest != null;

        if (shouldFlee) {
            if (unbeta_jolFleeGoal == null && !nearest.isCreative()) {
                // Survival: use proper FleeEntityGoal for pathfinding
                unbeta_jolFleeGoal = new FleeEntityGoal<>(
                        (net.minecraft.entity.mob.PathAwareEntity)(Object)this,
                        PlayerEntity.class, 10.0F, 1.2, 1.4,
                        e -> e instanceof ServerPlayerEntity sp2
                                && !sp2.isSpectator()
                                && !sp2.isCreative()
                                && sp2.getEquippedStack(EquipmentSlot.HEAD)
                                        .isOf(JackOLanternRegistry.UNLIT_ITEM));
                self.goalSelector.add(1, unbeta_jolFleeGoal);
            } else if (nearest.isCreative()) {
                // Creative: FleeEntityGoal won't find creative players, nudge directly
                if (unbeta_jolFleeGoal != null) {
                    self.goalSelector.remove(unbeta_jolFleeGoal);
                    unbeta_jolFleeGoal = null;
                }
                double dx = self.getX() - nearest.getX();
                double dz = self.getZ() - nearest.getZ();
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > 0.01) {
                    self.getNavigation().startMovingTo(
                            self.getX() + dx / dist * 5,
                            self.getY(),
                            self.getZ() + dz / dist * 5,
                            1.2);
                }
            }
        } else if (unbeta_jolFleeGoal != null) {
            self.goalSelector.remove(unbeta_jolFleeGoal);
            unbeta_jolFleeGoal = null;
        }
    }
}
