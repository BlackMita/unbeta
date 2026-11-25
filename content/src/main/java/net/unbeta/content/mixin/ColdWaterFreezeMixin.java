package net.unbeta.content.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.EntityTypeTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Freezing water: with its head under water where it would snow rather than rain, a creature
 * freezes just as in powder snow (frost over the screen, then freezing damage). Each piece of
 * leather armour slows the freezing (1 piece twice as slow, 2 three times, 3 four times), and a
 * full leather set stops it. Creatures that live or breathe under water never freeze this way.
 */
@Mixin(LivingEntity.class)
public abstract class ColdWaterFreezeMixin {

    /** Frozen ticks before this tick's vanilla freezing step (which thaws anything not in powder snow). */
    @Unique private int unbeta_frozenBefore;
    /** Ticks spent in freezing water, for pacing the slower freezing under leather. */
    @Unique private int unbeta_coldTicks;

    @Inject(method = "tickMovement", at = @At("HEAD"))
    private void unbeta_rememberFrost(CallbackInfo ci) {
        unbeta_frozenBefore = ((Entity) (Object) this).getFrozenTicks();
    }

    @Inject(method = "tickMovement", at = @At("TAIL"))
    private void unbeta_coldWater(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.getWorld().isClient || self.isDead() || !unbeta_inFreezingWater(self)) {
            unbeta_coldTicks = 0;
            return;
        }
        int leather = 0;
        for (ItemStack s : self.getArmorItems()) if (s.isIn(ItemTags.FREEZE_IMMUNE_WEARABLES)) leather++;
        if (leather >= 4) return; // a full leather set keeps the cold out

        unbeta_coldTicks++;
        int max = self.getMinFreezeDamageTicks();
        int frost = unbeta_frozenBefore;
        if (unbeta_coldTicks % (leather + 1) == 0) frost++;
        self.setFrozenTicks(Math.max(self.getFrozenTicks(), Math.min(max, frost)));

        // Vanilla only hurts frozen things that wear no leather at all; with 1-3 pieces, hurt them here.
        if (leather > 0 && self.age % 40 == 0 && self.isFrozen()) {
            int dmg = self.getType().isIn(EntityTypeTags.FREEZE_HURTS_EXTRA_TYPES) ? 5 : 1;
            self.damage(self.getDamageSources().freeze(), dmg);
        }
    }

    @Unique
    private static boolean unbeta_inFreezingWater(LivingEntity self) {
        if (!self.isSubmergedInWater() || self.canBreatheInWater()) return false;
        if (self.getType().isIn(EntityTypeTags.FREEZE_IMMUNE_ENTITY_TYPES)) return false;
        if (self instanceof net.minecraft.entity.mob.WaterCreatureEntity) return false;
        if (self instanceof PlayerEntity p && (p.isCreative() || p.isSpectator())) return false;
        BlockPos at = BlockPos.ofFloored(self.getEyePos());
        return self.getWorld().getBiome(at).value().getPrecipitation(at) == Biome.Precipitation.SNOW;
    }
}
