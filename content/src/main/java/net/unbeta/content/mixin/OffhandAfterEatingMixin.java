package net.unbeta.content.mixin;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.unbeta.content.client.eating.EatingGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Right after eating, the off hand neither places blocks nor uses items (see EatingGuard). */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class OffhandAfterEatingMixin {

    @Inject(method = "interactBlock", at = @At("HEAD"), cancellable = true)
    private void unbeta_noOffhandBlock(ClientPlayerEntity player, Hand hand, BlockHitResult hit,
                                       CallbackInfoReturnable<ActionResult> cir) {
        if (hand == Hand.OFF_HAND && EatingGuard.offhandLocked()) cir.setReturnValue(ActionResult.PASS);
    }

    @Inject(method = "interactItem", at = @At("HEAD"), cancellable = true)
    private void unbeta_noOffhandItem(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if (hand == Hand.OFF_HAND && EatingGuard.offhandLocked()) cir.setReturnValue(ActionResult.PASS);
    }
}
