package net.unbeta.content.mixin;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.CreeperIgniteGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.world.World;
import net.unbeta.content.boomspore.CreeperDataTracker;
import net.unbeta.content.boomspore.CreeperStalkGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreeperEntity.class)
public abstract class CreeperBehaviorMixin extends HostileEntity {

    protected CreeperBehaviorMixin(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
    }

    @Inject(method = "initDataTracker", at = @At("TAIL"))
    private void unbeta_registerPlantedTracker(CallbackInfo ci) {
        CreeperDataTracker.init(this.dataTracker);
    }

    @Inject(method = "initGoals", at = @At("TAIL"))
    private void unbeta_replaceGoals(CallbackInfo ci) {
        this.goalSelector.getGoals().removeIf(g ->
                g.getGoal() instanceof CreeperIgniteGoal
             || g.getGoal() instanceof MeleeAttackGoal);
        this.goalSelector.add(2, new CreeperStalkGoal((CreeperEntity)(Object)this));
    }
}
