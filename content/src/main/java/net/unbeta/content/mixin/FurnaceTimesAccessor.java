package net.unbeta.content.mixin;

import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractFurnaceBlockEntity.class)
public interface FurnaceTimesAccessor {
    @Accessor("burnTime") int unbeta_getBurnTime();
    @Accessor("burnTime") void unbeta_setBurnTime(int ticks);
    @Accessor("fuelTime") void unbeta_setFuelTime(int ticks);
    @Accessor("cookTime") int unbeta_getCookTime();
    @Accessor("cookTime") void unbeta_setCookTime(int ticks);
}
