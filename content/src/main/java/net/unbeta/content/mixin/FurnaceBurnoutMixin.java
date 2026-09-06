package net.unbeta.content.mixin;

import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.unbeta.content.furnace.FurnaceIgnitionTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.HashMap;
import java.util.Map;

/**
 * Clears furnace ignition only after it has been cold for 60 ticks (3 seconds).
 * Fuel-to-fuel transitions within one cooking job don't require re-lighting.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public class FurnaceBurnoutMixin {

    private static final Map<Long, Long> coldSince = new HashMap<>();
    private static final long COLD_THRESHOLD = 60L; // ticks before ignition clears

    @Inject(method = "tick", at = @At("TAIL"))
    private static void unbeta_clearIgnitionOnBurnout(World world, BlockPos pos,
                                                       BlockState state,
                                                       AbstractFurnaceBlockEntity blockEntity,
                                                       CallbackInfo ci) {
        if (world.isClient) return;
        long key = pos.asLong();
        boolean lit = state.get(AbstractFurnaceBlock.LIT);

        if (lit) {
            // Furnace is burning — reset cold timer
            coldSince.remove(key);
        } else if (FurnaceIgnitionTracker.isIgnited(pos)) {
            // Furnace is cold but was ignited — start/check cold timer
            long now = world.getTime();
            coldSince.putIfAbsent(key, now);
            if (now - coldSince.get(key) >= COLD_THRESHOLD) {
                FurnaceIgnitionTracker.IGNITED.remove(key);
                coldSince.remove(key);
            }
        }
    }
}
