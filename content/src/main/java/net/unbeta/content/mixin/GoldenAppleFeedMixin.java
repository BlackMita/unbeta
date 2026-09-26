package net.unbeta.content.mixin;

import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.unbeta.content.corruption.CorruptionCure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Golden apple on livestock (cow, pig, sheep, chicken): a bitten one eats it and is cured, a
 * healthy one refuses. Cow/pig/sheep interactMob all fall through to AnimalEntity's for an
 * item they don't use; chickens don't override it at all. Other animals pass straight through.
 */
@Mixin(AnimalEntity.class)
public abstract class GoldenAppleFeedMixin {

    @Inject(method = "interactMob", at = @At("HEAD"), cancellable = true)
    private void unbeta_goldenApple(PlayerEntity player, Hand hand,
                                    CallbackInfoReturnable<ActionResult> cir) {
        ActionResult result = CorruptionCure.tryFeed((AnimalEntity)(Object)this, player, hand);
        if (result != ActionResult.PASS) cir.setReturnValue(result);
    }
}
