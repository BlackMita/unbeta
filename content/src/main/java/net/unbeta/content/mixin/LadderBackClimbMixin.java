package net.unbeta.content.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.LadderBlock;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ladders (and Burnt's burnt ladder) climb from both sides. Vanilla only counts you as climbing while you stand IN the
 * ladder's block; a freestanding ladder (nothing behind it) can now also be climbed by
 * pressing against its back.
 */
@Mixin(LivingEntity.class)
public abstract class LadderBackClimbMixin {

    /** Which way a ladder (vanilla, Smart Ladders or Burnt's burnt ladder) faces; null if not a ladder. */
    private static Direction facing(BlockState s) {
        if (s.getBlock() instanceof LadderBlock) return s.get(LadderBlock.FACING);
        if (!s.isIn(net.minecraft.registry.tag.BlockTags.CLIMBABLE)) return null;
        if (s.getBlock().getStateManager().getProperty("facing") instanceof net.minecraft.state.property.DirectionProperty p) return s.get(p);
        return null;
    }

    @Inject(method = "isClimbing", at = @At("RETURN"), cancellable = true)
    private void unbeta_climbFromBehind(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) return;
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.isSpectator()) return;

        BlockPos feet = self.getBlockPos();
        Box reach = self.getBoundingBox().expand(0.06, 0.0, 0.06);
        for (Direction d : Direction.Type.HORIZONTAL) {
            BlockPos at = feet.offset(d);
            BlockState s = self.getWorld().getBlockState(at);
            // A ladder whose FRONT faces away from us (direction d) has its back to us.
            if (facing(s) == d && reach.intersects(new Box(at))) {
                cir.setReturnValue(true);
                return;
            }
        }
    }
}
