package net.unbeta.content.mixin;

import net.minecraft.entity.mob.CaveSpiderEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.unbeta.content.spider.SpiderBabies;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Baby spiders (cave spiders) grow up over time - see SpiderBabies.tickBaby. */
@Mixin(SpiderEntity.class)
public abstract class SpiderGrowUpMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void unbeta_growUp(CallbackInfo ci) {
        if ((Object)this instanceof CaveSpiderEntity baby) {
            SpiderBabies.tickBaby(baby);
        }
    }
}
