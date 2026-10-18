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

        boolean isPassive = self instanceof AnimalEntity && !(self instanceof WolfEntity)
                && !(self instanceof net.unbeta.content.corruption.CorruptedAnimal);
        boolean isVillager = self instanceof VillagerEntity;
        if (!isPassive && !isVillager) return;

        // Find nearest JoL-wearing non-spectator player using server list
        ServerPlayerEntity nearest = null;
        double nearestDist = 16.0 * 16.0;
        for (ServerPlayerEntity sp : sw.getPlayers()) {
            if (sp.isSpectator()) continue;
            if (!net.unbeta.content.light.Beacons.wearsJol(sp)) continue;
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
                                && net.unbeta.content.light.Beacons.wearsJol(sp2));
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

        // Scarecrows: an armour stand wearing a Jack o'Lantern (lit or not) within 10 blocks.
        if (!shouldFlee && self.age % 20 == 0) {
            var crows = sw.getEntitiesByClass(net.minecraft.entity.decoration.ArmorStandEntity.class,
                    self.getBoundingBox().expand(10.0), net.unbeta.content.light.Beacons::wearsJol);
            if (!crows.isEmpty()) {
                var crow = crows.get(0);
                double dx = self.getX() - crow.getX();
                double dz = self.getZ() - crow.getZ();
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > 0.01) {
                    self.getNavigation().startMovingTo(self.getX() + dx / dist * 6, self.getY(),
                            self.getZ() + dz / dist * 6, 1.2);
                }
            }
        }
    }
}
