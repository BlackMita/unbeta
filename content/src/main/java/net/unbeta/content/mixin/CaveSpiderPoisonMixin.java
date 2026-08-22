package net.unbeta.content.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.CaveSpiderEntity;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Cave spiders do not apply poison in Unbeta.
 * We cancel CaveSpiderEntity.tryAttack entirely and call MobEntity.tryAttack
 * directly via shadow to avoid the recursive call through SpiderEntity.
 */
@Mixin(CaveSpiderEntity.class)
public abstract class CaveSpiderPoisonMixin extends MobEntity {

    protected CaveSpiderPoisonMixin() { super(null, null); }

    @Inject(method = "tryAttack", at = @At("HEAD"), cancellable = true)
    private void unbeta_noCaveSpiderPoison(Entity target,
                                            CallbackInfoReturnable<Boolean> cir) {
        // Call MobEntity.tryAttack directly, skipping SpiderEntity and
        // CaveSpiderEntity overrides entirely — no poison, no recursion.
        cir.setReturnValue(super.tryAttack(target));
    }
}
