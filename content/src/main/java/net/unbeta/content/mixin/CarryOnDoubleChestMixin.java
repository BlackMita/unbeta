package net.unbeta.content.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.unbeta.content.lockey.CarryOnCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.BiFunction;

/** Carry On pickups (doubles too heavy; siege stays out of the permission check) and placements. Optional. */
@Pseudo
@Mixin(targets = "tschipp.carryon.common.carry.PickupHandler", remap = false)
public abstract class CarryOnDoubleChestMixin {

    @Inject(method = "tryPickUpBlock", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void unbeta_beforePickup(ServerPlayerEntity player, BlockPos pos, World world,
                                            BiFunction<?, ?, ?> check, CallbackInfoReturnable<Boolean> cir) {
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock
                && state.contains(ChestBlock.CHEST_TYPE)
                && state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE) {
            player.sendMessage(Text.literal("A double chest is too heavy to carry."), true);
            cir.setReturnValue(false);
            return;
        }
        // Carry On asks "may this player break it?" through the block-break event; Lockey's
        // siege must not mistake that question for a real swing.
        CarryOnCompat.setPickingUp(true);
    }

    @Inject(method = "tryPickUpBlock", at = @At("RETURN"), remap = false, require = 0)
    private static void unbeta_afterPickup(ServerPlayerEntity player, BlockPos pos, World world,
                                           BiFunction<?, ?, ?> check, CallbackInfoReturnable<Boolean> cir) {
        CarryOnCompat.setPickingUp(false);
    }
}
