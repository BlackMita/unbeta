package net.unbeta.content.mixin;

import net.minecraft.structure.StrongholdGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(StrongholdGenerator.SquareRoom.class)
public interface StrongholdSquareRoomAccessor {
    @Accessor("roomType")
    int unbeta_getRoomType();
}
