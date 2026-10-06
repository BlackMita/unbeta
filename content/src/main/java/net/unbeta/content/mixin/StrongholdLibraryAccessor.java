package net.unbeta.content.mixin;

import net.minecraft.structure.StrongholdGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(StrongholdGenerator.Library.class)
public interface StrongholdLibraryAccessor {
    @Accessor("tall")
    boolean unbeta_isTall();
}
