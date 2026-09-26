package net.unbeta.content.mixin;

import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.unbeta.content.corruption.BittenTemptGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Livestock get the bitten-tempt goal (active only while bitten). Priority 2 puts it above
 * their normal food temptation (3) but below panic (1). Corrupted animals inherit this and
 * then clear it, along with every other passive goal, in their own initGoals.
 */
@Mixin({CowEntity.class, PigEntity.class, SheepEntity.class, ChickenEntity.class})
public abstract class BittenTemptMixin {

    @Inject(method = "initGoals", at = @At("TAIL"))
    private void unbeta_bittenTempt(CallbackInfo ci) {
        PathAwareEntity self = (PathAwareEntity)(Object)this;
        self.goalSelector.add(2, new BittenTemptGoal(self));
    }
}
