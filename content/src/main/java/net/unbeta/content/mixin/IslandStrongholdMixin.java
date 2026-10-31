package net.unbeta.content.mixin;

import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.chunk.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.gen.chunk.placement.StructurePlacementCalculator;
import net.unbeta.content.island.Island;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Island mode: strongholds are placed under the island instead of in rings far out at sea. */
@Mixin(StructurePlacementCalculator.class)
public abstract class IslandStrongholdMixin {

    @Inject(method = "getPlacementPositions", at = @At("HEAD"), cancellable = true)
    private void unbeta_islandStrongholds(ConcentricRingsStructurePlacement placement, CallbackInfoReturnable<List<ChunkPos>> cir) {
        if (Island.active() && (Object) this == Island.CALCULATOR) cir.setReturnValue(Island.strongholds());
    }
}
