package net.unbeta.content.mixin;

import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lockey durability as a dropped item.
 *
 * <p>Never despawns, survives fire and explosions, dies to lava. Lava is the one way a
 * key can be lost, which is what makes "Will never unlock" reachable at all.
 *
 * <p>Despawning is prevented with vanilla's own NEVER_DESPAWN_AGE sentinel (-32768),
 * which ItemEntity.tick already checks, rather than by fighting the age counter.
 */
@Mixin(ItemEntity.class)
public abstract class LockeyItemEntityMixin {

    @Shadow private int itemAge;

    /** Vanilla discards a dropped item once itemAge reaches this. */
    private static final int DESPAWN_AGE = 6000;

    private boolean unbeta_isLockey() {
        ItemEntity self = (ItemEntity)(Object)this;
        return net.unbeta.content.lockey.LockeyItem.isLockey(self.getStack());
    }

    /**
     * Roll the age over before it can hit the despawn threshold.
     *
     * <p>Deliberately NOT the -32768 never-despawn sentinel: the renderer derives the
     * item's spin and bob from getItemAge(), so a pinned age freezes the item solid and
     * it reads as broken. Letting it age and wrapping keeps the animation, and keeps
     * merging with other stacks working, while it can never grow old enough to vanish.
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void unbeta_neverDespawn(CallbackInfo ci) {
        if (!unbeta_isLockey()) return;
        if (this.itemAge >= DESPAWN_AGE - 100) {
            this.itemAge = 0;
        }
    }

    /** Fire and explosions bounce off; lava consumes the key and retires it. */
    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void unbeta_durability(DamageSource source, float amount,
                                   CallbackInfoReturnable<Boolean> cir) {
        if (!unbeta_isLockey()) return;
        ItemEntity self = (ItemEntity)(Object)this;

        boolean lava = source.isOf(DamageTypes.LAVA);
        if (lava) {
            // Retire the key BEFORE the entity goes away, or we lose the id and the
            // chest is left reporting "unknown" instead of the honest terminal message.
            if (self.getWorld() instanceof net.minecraft.server.world.ServerWorld sw) {
                java.util.UUID id =
                    net.unbeta.content.lockey.LockeyItem.getId(self.getStack());
                if (id != null) net.unbeta.content.lockey.LockeyState.revoke(sw, id);
            }
            return; // let vanilla destroy it
        }

        // Everything else - fire, explosions, cacti, falling anvils - leaves it be.
        cir.setReturnValue(false);
    }
}
