package net.unbeta.content.mixin;

import net.minecraft.block.DetectorRailBlock;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.unbeta.content.train.TrainCart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** A cart jumping over a detector rail doesn't trip it. */
@Mixin(DetectorRailBlock.class)
public abstract class DetectorRailAirMixin {

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Inject(method = "getCarts", at = @At("RETURN"), cancellable = true)
    private void unbeta_skipAirborne(World world, BlockPos pos, Class entityClass, Predicate<Entity> predicate, CallbackInfoReturnable<List> cir) {
        List found = cir.getReturnValue();
        if (found == null || found.isEmpty()) return;
        List kept = new ArrayList(found.size());
        for (Object o : found) {
            if (o instanceof TrainCart c && (c.unbeta_airborne() || c.unbeta_derailed())) continue;
            kept.add(o);
        }
        if (kept.size() != found.size()) cir.setReturnValue(kept);
    }
}
