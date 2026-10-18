package net.unbeta.content.mixin;

import net.minecraft.entity.mob.GuardianEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets the Guardian Post aim (and drop) the Guardian beam it inherits. */
@Mixin(GuardianEntity.class)
public interface GuardianBeamInvoker {

    @Invoker("setBeamTarget")
    void unbeta$setBeamTarget(int entityId);
}
