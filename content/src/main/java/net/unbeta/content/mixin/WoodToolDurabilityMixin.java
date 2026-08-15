package net.unbeta.content.mixin;

import net.minecraft.item.ToolMaterials;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Constant;

/** Halves wood tool durability from 59 to 29. */
@Mixin(ToolMaterials.class)
public class WoodToolDurabilityMixin {

    @ModifyConstant(method = "<clinit>", constant = @Constant(intValue = 59))
    private static int unbeta_halfWoodDurability(int original) {
        return 29;
    }
}
