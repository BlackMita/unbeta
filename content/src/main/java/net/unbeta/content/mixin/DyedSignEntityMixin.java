package net.unbeta.content.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.unbeta.content.dye.DyedBlocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The dyed signs use vanilla's sign block entity, which only knows vanilla's signs: let it take ours too. */
@Mixin(BlockEntityType.class)
public abstract class DyedSignEntityMixin {

    @Inject(method = "supports", at = @At("HEAD"), cancellable = true)
    private void unbeta_dyedSigns(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this != BlockEntityType.SIGN) return;
        DyedBlocks.Entry e = DyedBlocks.entry(state.getBlock());
        if (e != null && e.color() != null
                && (e.shape() == DyedBlocks.Shape.SIGN || e.shape() == DyedBlocks.Shape.WALL_SIGN)) cir.setReturnValue(true);
    }
}
