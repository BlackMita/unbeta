package net.unbeta.content.mixin;

import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.chunk.placement.RandomSpreadStructurePlacement;
import net.unbeta.content.island.Island;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Island mode: the Darkhold and the Skyhold are far too rare to land on an island by chance, so
 * each gets one spot on it (if the ground there suits it, it's built).
 */
@Mixin(RandomSpreadStructurePlacement.class)
public abstract class IslandSpreadMixin {

    private static final int DARKHOLD_SALT = 640217943, SKYHOLD_SALT = 1873462913;

    @Inject(method = "getStartChunk", at = @At("HEAD"), cancellable = true)
    private void unbeta_onTheIsland(long seed, int chunkX, int chunkZ, CallbackInfoReturnable<ChunkPos> cir) {
        if (!Island.active()) return;
        int salt = ((StructurePlacementAccessor) (Object) this).unbeta_salt();
        if (salt == DARKHOLD_SALT || salt == SKYHOLD_SALT) cir.setReturnValue(Island.spotFor(salt));
    }
}
