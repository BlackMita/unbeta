package net.unbeta.hg.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.GameRules;
import net.unbeta.content.obsidiandoor.XpHelper;
import net.unbeta.hg.UnbetaHG;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A dead player drops all the experience they carried (vanilla drops only a little and destroys
 * the rest). Killed by another player, they drop at least 1 level's worth (7 points), so even
 * a poor player is worth something - but not when they die on their own.
 */
@Mixin(PlayerEntity.class)
public abstract class DeathXpMixin {

    @Inject(method = "getXpToDrop", at = @At("HEAD"), cancellable = true)
    private void unbetaHg_dropItAll(CallbackInfoReturnable<Integer> cir) {
        if (!UnbetaHG.ACTIVE) return;
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (self.isSpectator() || self.getWorld().getGameRules().getBoolean(GameRules.KEEP_INVENTORY)) return;
        int points = XpHelper.totalPoints(self);
        LivingEntity killer = self.getPrimeAdversary();
        if (killer instanceof PlayerEntity && killer != self) points = Math.max(points, 7);   // 1 level's worth
        cir.setReturnValue(points);
    }
}
