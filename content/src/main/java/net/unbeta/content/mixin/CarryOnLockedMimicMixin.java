package net.unbeta.content.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.server.network.ServerPlayerEntity;
import net.unbeta.content.mimic.MimicLocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Function;

/**
 * Carry On refuses monsters in survival (pickupHostileMobs is off). A LOCKED mimic is a chest
 * as far as anyone's concerned, so to that check it isn't a monster. Unlocked mimics are
 * still refused.
 */
@Pseudo
@Mixin(targets = "tschipp.carryon.common.carry.PickupHandler", remap = false)
public abstract class CarryOnLockedMimicMixin {

    @Redirect(method = "tryPickupEntity",
              at = @At(value = "INVOKE", target = "Lnet/minecraft/class_1299;method_5891()Lnet/minecraft/class_1311;"),
              remap = false)
    private static SpawnGroup unbeta_lockedMimicIsCargo(EntityType<?> type, ServerPlayerEntity player,
                                                        Entity entity, Function<?, ?> check) {
        if (MimicLocks.lockOf(entity) != null) return SpawnGroup.MISC;
        return type.getSpawnGroup();
    }
}
