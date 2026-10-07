package net.unbeta.content.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ShulkerBulletEntity;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ShulkerBulletEntity.class)
public interface ShulkerBulletAccessor {
    @Accessor("target")
    void unbeta_setTarget(Entity target);

    @Accessor("direction")
    void unbeta_setDirection(Direction direction);

    @Invoker("changeTargetDirection")
    void unbeta_changeTargetDirection(Direction.Axis axis);
}
