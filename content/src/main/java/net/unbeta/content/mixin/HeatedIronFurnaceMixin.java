package net.unbeta.content.mixin;

import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.unbeta.content.furnace.FurnaceIgnitionTracker;
import net.unbeta.content.heatediron.HeatedIron;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A Heated Iron Block in the fuel slot is its own heat: it lights the furnace without a flame,
 * is never used up, and cooks at half speed until it has cooled into plain iron.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class HeatedIronFurnaceMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private static void unbeta_heatedIronFuel(World world, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity furnace, CallbackInfo ci) {
        if (world.isClient) return;
        ItemStack fuel = furnace.getStack(1);
        if (!fuel.isOf(HeatedIron.ITEM)) return;
        if (HeatedIron.cold(fuel, world)) {
            furnace.setStack(1, HeatedIron.cooled(fuel));
            furnace.markDirty();
            return;
        }
        FurnaceTimesAccessor times = (FurnaceTimesAccessor) furnace;
        long left = Math.max(2, HeatedIron.heatUntil(fuel, world) - world.getTime());
        times.unbeta_setFuelTime((int) HeatedIron.MAX_HEAT);
        times.unbeta_setBurnTime((int) Math.min(left, HeatedIron.MAX_HEAT));   // the flame shows the heat left
        // Half speed: every other tick, take back the step the furnace is about to make.
        if (world.getTime() % 2 == 0 && times.unbeta_getCookTime() > 0) times.unbeta_setCookTime(times.unbeta_getCookTime() - 1);
        FurnaceIgnitionTracker.ignite(pos);
        if (!state.get(AbstractFurnaceBlock.LIT)) world.setBlockState(pos, state.with(AbstractFurnaceBlock.LIT, true), Block.NOTIFY_ALL);
    }

    /** It goes in the fuel slot (by hand or hopper). */
    @Inject(method = "canUseAsFuel", at = @At("HEAD"), cancellable = true)
    private static void unbeta_heatedIronIsFuel(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.isOf(HeatedIron.ITEM)) cir.setReturnValue(true);
    }
}
