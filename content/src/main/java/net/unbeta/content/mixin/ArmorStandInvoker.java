package net.unbeta.content.mixin;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.decoration.ArmorStandEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ArmorStandEntity.class)
public interface ArmorStandInvoker {
    @Invoker("updateHealth") void unbeta_updateHealth(DamageSource source, float amount);
}
