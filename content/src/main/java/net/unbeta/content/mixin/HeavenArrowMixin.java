package net.unbeta.content.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.unbeta.content.arrow.HeavenArrowAccess;
import net.unbeta.content.arrow.HeavenArrows;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Heaven's Arrows - see HeavenArrows. A flag on the ordinary arrow, saved with it. */
@Mixin(ArrowEntity.class)
public abstract class HeavenArrowMixin implements HeavenArrowAccess {

    @Unique private boolean unbeta_heaven;

    @Override
    public boolean unbeta$isHeaven() {
        return unbeta_heaven;
    }

    @Override
    public void unbeta$setHeaven(boolean heaven) {
        unbeta_heaven = heaven;
    }

    @Inject(method = "onHit", at = @At("TAIL"))
    private void unbeta_heavenLift(LivingEntity target, CallbackInfo ci) {
        if (!unbeta_heaven) return;
        ArrowEntity self = (ArrowEntity) (Object) this;
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION,
                HeavenArrows.LEVITATION_TICKS, HeavenArrows.LEVITATION_LEVEL), self.getOwner());
    }

    @Inject(method = "asItemStack", at = @At("HEAD"), cancellable = true)
    private void unbeta_heavenPickup(CallbackInfoReturnable<ItemStack> cir) {
        if (unbeta_heaven && HeavenArrows.HEAVENS_ARROW != null) cir.setReturnValue(new ItemStack(HeavenArrows.HEAVENS_ARROW));
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void unbeta_saveHeaven(NbtCompound nbt, CallbackInfo ci) {
        if (unbeta_heaven) nbt.putBoolean("UnbetaHeaven", true);
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void unbeta_loadHeaven(NbtCompound nbt, CallbackInfo ci) {
        unbeta_heaven = nbt.getBoolean("UnbetaHeaven");
    }
}
