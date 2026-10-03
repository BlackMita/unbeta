package net.unbeta.content.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.SilverfishEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.unbeta.content.stronghold.Breach;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A silverfish calling its friends breaks the infested blocks they burst out of - neither a
 * player break nor an explosion, so it's caught here, where any entity breaks a block.
 * Recorded before the block is removed, so its original state is still readable. A whole
 * nest bursting out lies within the merge radius, so it becomes one breach.
 */
@Mixin(World.class)
public abstract class SilverfishBreachMixin {

    @Inject(method = "breakBlock(Lnet/minecraft/util/math/BlockPos;ZLnet/minecraft/entity/Entity;I)Z",
            at = @At("HEAD"))
    private void unbeta_silverfishBreach(BlockPos pos, boolean drop, Entity breakingEntity,
                                         int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir) {
        if (!(breakingEntity instanceof SilverfishEntity)) return;
        if (!((Object)this instanceof ServerWorld sw)) return;
        Breach.record(sw, java.util.Map.of(pos.toImmutable(), sw.getBlockState(pos)));
    }
}
