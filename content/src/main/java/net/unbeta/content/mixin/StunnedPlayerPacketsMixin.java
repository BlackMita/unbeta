package net.unbeta.content.mixin;

import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.unbeta.content.taser.Taser;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A stunned player is locked: no switching or scrolling the hotbar, no moving items about, no
 * swapping hands, dropping or digging. (Clicks on things are stopped in Taser itself.)
 */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class StunnedPlayerPacketsMixin {

    @Shadow public ServerPlayerEntity player;

    private static final String MAIN_THREAD = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/server/world/ServerWorld;)V";

    @Inject(method = "onUpdateSelectedSlot", at = @At(value = "INVOKE", target = MAIN_THREAD, shift = At.Shift.AFTER), cancellable = true)
    private void unbeta_stunnedSlot(UpdateSelectedSlotC2SPacket packet, CallbackInfo ci) {
        if (Taser.isStunned(player)) {
            player.networkHandler.sendPacket(new UpdateSelectedSlotS2CPacket(player.getInventory().selectedSlot));
            ci.cancel();
        }
    }

    @Inject(method = "onClickSlot", at = @At(value = "INVOKE", target = MAIN_THREAD, shift = At.Shift.AFTER), cancellable = true)
    private void unbeta_stunnedClick(ClickSlotC2SPacket packet, CallbackInfo ci) {
        if (Taser.isStunned(player)) {
            player.currentScreenHandler.syncState();
            ci.cancel();
        }
    }

    @Inject(method = "onPlayerAction", at = @At(value = "INVOKE", target = MAIN_THREAD, shift = At.Shift.AFTER), cancellable = true)
    private void unbeta_stunnedAction(PlayerActionC2SPacket packet, CallbackInfo ci) {
        if (Taser.isStunned(player)) {
            player.currentScreenHandler.syncState();
            ci.cancel();
        }
    }
}
