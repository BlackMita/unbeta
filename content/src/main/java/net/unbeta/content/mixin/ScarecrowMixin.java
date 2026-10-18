package net.unbeta.content.mixin;

import net.minecraft.entity.DamageUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.world.ServerWorld;
import net.unbeta.content.light.Beacons;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Scarecrows: armour stands wearing a Jack o'Lantern. Vanilla armour stands ignore mobs' hits
 * (and an arrow breaks one outright); a scarecrow instead has health, takes mobs' hits and
 * arrows reduced by the armour it wears, and breaks when worn down - dropping what it wore.
 * Players still knock one over the vanilla way.
 */
@Mixin(ArmorStandEntity.class)
public abstract class ScarecrowMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void unbeta_scarecrowTick(CallbackInfo ci) {
        ArmorStandEntity self = (ArmorStandEntity) (Object) this;
        if (self.getWorld() instanceof ServerWorld world) Beacons.tickStand(world, self);
    }

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void unbeta_scarecrowTakesHits(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        ArmorStandEntity self = (ArmorStandEntity) (Object) this;
        if (!(self.getWorld() instanceof ServerWorld world) || self.isRemoved() || !Beacons.wearsJol(self)) return;
        Entity attacker = source.getAttacker();
        if (!(attacker instanceof MobEntity) || source.isIn(DamageTypeTags.IS_EXPLOSION)) return;   // players, blasts, fire: vanilla
        float dealt = DamageUtil.getDamageLeft(amount, (float) self.getArmor(),
                (float) self.getAttributeValue(EntityAttributes.GENERIC_ARMOR_TOUGHNESS));
        world.sendEntityStatus(self, (byte) 32);   // the hit wobble and sound
        ((ArmorStandInvoker) self).unbeta_updateHealth(source, dealt);
        cir.setReturnValue(true);
    }
}
