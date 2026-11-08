package net.unbeta.content.mixin;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Whether a rider is holding jump (the server learns it from the riding input packet). */
@Mixin(LivingEntity.class)
public interface LivingEntityJumpAccessor {
    @Accessor("jumping")
    boolean unbeta_isJumping();
}
