package net.unbeta.content.mixin;

import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.CaveSpiderEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Cave spiders are Unbeta's baby spiders: 2 health (1 heart), down from 12. One full
 * wooden-sword swing (4) kills; one bare-handed punch (1) doesn't. Applies to newly spawned
 * cave spiders - ones already saved in a world keep the max health saved with them.
 */
@Mixin(CaveSpiderEntity.class)
public abstract class CaveSpiderHealthMixin {

    @Inject(method = "createCaveSpiderAttributes", at = @At("RETURN"))
    private static void unbeta_babyHealth(CallbackInfoReturnable<DefaultAttributeContainer.Builder> cir) {
        cir.getReturnValue().add(EntityAttributes.GENERIC_MAX_HEALTH, 2.0);
    }
}
