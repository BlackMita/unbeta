package net.unbeta.content.mixin;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.util.math.MathHelper;
import net.unbeta.content.enderman.EndermanDreadState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Enderman dread: reuses the vanilla portal-wobble visual (ClientPlayerEntity's
 * nauseaIntensity float) and drives it from the nearest ANGRY (hostile-and-hunting)
 * enderman's distance, instead of portal proximity or the Nausea status effect.
 *
 * <p>getTarget() is server-authoritative and is never synced to the client - it always
 * reads null client-side, confirmed during testing. isAngry() reads the ANGRY
 * TrackedData<Boolean>, which IS synced (the client needs it to predict chase/stare
 * behaviour locally), and flips false the instant the server clears the target - e.g.
 * the moment an enderman flees from light. That's our client-visible proxy for
 * "hunting me right now."
 *
 * <p>updateNausea() is the single place vanilla writes this float each client tick -
 * real portal entry, real Nausea potion, and decay-to-zero when neither applies. We
 * inject at TAIL, after vanilla has done its own computation, and only ever push the
 * value UP toward our enderman-driven target - never down, never resetting vanilla's
 * own decay.
 *
 * <p>EndermanDreadState.active is NOT reset every tick. It stays true for the entire
 * decay tail after the enderman stops being angry (e.g. flees into light) and only
 * clears once nauseaIntensity has actually decayed to zero, or a real Nausea potion
 * takes over. Without this, the swirl-overlay suppression (see
 * PortalOverlaySuppressMixin) would unflag itself the instant the enderman de-aggros,
 * while several ticks of residual intensity remain - producing a brief flash of the
 * portal swirl right as the enderman flees. Ownership of "why is this nonzero" has to
 * persist through the whole decay, not just the tick we raised it.
 *
 * <p>Range: wobble starts at 23 blocks - one block inside the 24-block proximity-aggro
 * trigger, so it begins almost the instant the enderman notices the player rather than
 * waiting for it to close half the distance. Reaches half of max (0.5) at 2 blocks
 * (melee range). Eased (quadratic) so it builds slowly at range and sharpens right
 * before the enderman is on top of the player.
 */
@Mixin(ClientPlayerEntity.class)
public abstract class EndermanDreadMixin {

    private static final double START_DIST = 23.0; // one block inside the 24-block proximity-aggro range
    private static final double END_DIST = 2.0;
    private static final float MAX_INTENSITY = 0.5F;

    @Inject(method = "updateNausea", at = @At("TAIL"))
    private void unbeta_endermanDread(CallbackInfo ci) {
        ClientPlayerEntity self = (ClientPlayerEntity)(Object)this;
        if (self.getWorld() == null) return;

        // A real Nausea potion takes precedence - don't claim ownership of it.
        if (self.hasStatusEffect(StatusEffects.NAUSEA)) {
            EndermanDreadState.active = false;
            return;
        }

        double nearestDist = Double.MAX_VALUE;
        for (EndermanEntity e : self.getWorld().getEntitiesByClass(
                EndermanEntity.class,
                self.getBoundingBox().expand(START_DIST),
                EndermanEntity::isAngry)) {
            double d = self.distanceTo(e);
            if (d < nearestDist) nearestDist = d;
        }

        float target = 0.0F;
        if (nearestDist <= START_DIST) {
            double clamped = MathHelper.clamp(nearestDist, END_DIST, START_DIST);
            double t = 1.0 - (clamped - END_DIST) / (START_DIST - END_DIST);
            float eased = (float) (t * t);
            target = eased * MAX_INTENSITY;
        }

        if (target > self.nauseaIntensity) {
            // Actively raising it this tick - we're the source.
            self.nauseaIntensity = target;
            EndermanDreadState.active = true;
        } else if (self.nauseaIntensity <= 0.0F) {
            // Fully decayed - nothing left to protect, safe to clear.
            EndermanDreadState.active = false;
        }
        // else: nauseaIntensity > 0 but we're not raising it this tick (enderman fled
        // or went out of range). If we already owned it, KEEP owning it through the
        // decay tail - do not touch the flag here.
    }
}
