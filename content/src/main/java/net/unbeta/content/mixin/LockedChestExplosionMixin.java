package net.unbeta.content.mixin;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Locked chests survive explosions.
 *
 * <p>Hooked on Explosion's own call to behavior.canDestroyBlock rather than on
 * ExplosionBehavior itself: EntityExplosionBehavior overrides that method, so a mixin on
 * the base class never runs for TNT or creepers. Every explosion consumes the answer
 * here, whichever behavior produced it.
 */
@Mixin(Explosion.class)
public abstract class LockedChestExplosionMixin {

    @Shadow @Final private World world;

    @Redirect(
        method = "collectBlocksAndDamageEntities",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/world/explosion/ExplosionBehavior;canDestroyBlock("
                        + "Lnet/minecraft/world/explosion/Explosion;"
                        + "Lnet/minecraft/world/BlockView;"
                        + "Lnet/minecraft/util/math/BlockPos;"
                        + "Lnet/minecraft/block/BlockState;F)Z")
    )
    private boolean unbeta_lockedChestBlastProof(
            net.minecraft.world.explosion.ExplosionBehavior behavior,
            Explosion explosion, net.minecraft.world.BlockView view,
            BlockPos pos, net.minecraft.block.BlockState state, float power) {

        if (state.getBlock() instanceof net.minecraft.block.ChestBlock
                && this.world instanceof net.minecraft.server.world.ServerWorld sw
                && net.unbeta.content.lockey.LockeyState.isLocked(sw, pos)) {
            return false;
        }
        return behavior.canDestroyBlock(explosion, view, pos, state, power);
    }
}
