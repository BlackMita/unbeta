package net.unbeta.content.mixin;

import net.minecraft.entity.vehicle.FurnaceMinecartEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Which way a furnace minecart is pushing. */
@Mixin(FurnaceMinecartEntity.class)
public interface FurnaceMinecartAccessor {
    @Accessor("pushX") double unbeta_pushX();
    @Accessor("pushX") void unbeta_setPushX(double x);
    @Accessor("pushZ") double unbeta_pushZ();
    @Accessor("pushZ") void unbeta_setPushZ(double z);
}
