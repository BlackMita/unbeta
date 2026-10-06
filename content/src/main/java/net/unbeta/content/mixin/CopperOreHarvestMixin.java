package net.unbeta.content.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.PickaxeItem;
import net.minecraft.registry.tag.BlockTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Any pickaxe can mine copper ore - wood included (vanilla needs stone). Harvesting
 * decides both mining speed and whether the block drops anything.
 */
@Mixin(PlayerEntity.class)
public abstract class CopperOreHarvestMixin {

    @Inject(method = "canHarvest", at = @At("RETURN"), cancellable = true)
    private void unbeta_anyPickaxeMinesCopper(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() || !state.isIn(BlockTags.COPPER_ORES)) return;
        PlayerEntity self = (PlayerEntity)(Object)this;
        if (self.getMainHandStack().getItem() instanceof PickaxeItem) cir.setReturnValue(true);
    }
}
