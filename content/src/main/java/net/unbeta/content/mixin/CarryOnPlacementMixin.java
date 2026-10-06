package net.unbeta.content.mixin;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.unbeta.content.lockey.CarryOnCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.BiFunction;

/** Notes each successful Carry On placement, for the brief no-open grace afterwards. Optional. */
@Pseudo
@Mixin(targets = "tschipp.carryon.common.carry.PlacementHandler", remap = false)
public abstract class CarryOnPlacementMixin {

    @Inject(method = "tryPlaceBlock", at = @At("RETURN"), remap = false, require = 0)
    private static void unbeta_placed(ServerPlayerEntity player, BlockPos pos, Direction side,
                                      BiFunction<?, ?, ?> check, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) CarryOnCompat.notePlaced(player);
    }
}
