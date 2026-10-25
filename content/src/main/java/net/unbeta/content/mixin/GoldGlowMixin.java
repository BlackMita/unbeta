package net.unbeta.content.mixin;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A Gold Block gives off a faint glow (light 3). */
@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class GoldGlowMixin {

    @Shadow public abstract Block getBlock();

    @Inject(method = "getLuminance", at = @At("HEAD"), cancellable = true)
    private void unbeta_goldGlow(CallbackInfoReturnable<Integer> cir) {
        if (getBlock() == Blocks.GOLD_BLOCK) cir.setReturnValue(3);
    }
}
