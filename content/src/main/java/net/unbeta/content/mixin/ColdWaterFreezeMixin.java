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
 * Freezing water: in any water where it would snow rather than rain, a creature freezes twice as
 * fast as in powder snow (frost over the screen, then freezing damage). Each piece of leather
 * armour slows the freezing (1 piece twice as slow, 2 three times, 3 four times), and a full
 * leather set stops it. Creatures that live or breathe under water never freeze this way.
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
        if (unbeta_coldTicks % (leather + 1) == 0) frost += 2;
        self.setFrozenTicks(Math.max(self.getFrozenTicks(), Math.min(max, frost)));

        // Vanilla's freezing damage never fires here (it checks before this sets the frost back up,
        // and skips anything wearing leather), so hurt frozen things here, as powder snow would.
        if (!self.inPowderSnow && self.age % 40 == 0 && self.isFrozen()) {
            int dmg = self.getType().isIn(EntityTypeTags.FREEZE_HURTS_EXTRA_TYPES) ? 5 : 1;
            self.damage(self.getDamageSources().freeze(), dmg);
        }
    }

    @Unique
    private static boolean unbeta_inFreezingWater(LivingEntity self) {
        if (!self.isTouchingWater() || self.canBreatheInWater()) return false;
        if (self.getType().isIn(EntityTypeTags.FREEZE_IMMUNE_ENTITY_TYPES)) return false;
        if (self instanceof net.minecraft.entity.mob.WaterCreatureEntity) return false;
        if (self instanceof PlayerEntity p && (p.isCreative() || p.isSpectator())) return false;
        BlockPos at = self.getBlockPos();
        return unbeta_snowsAt(self.getWorld(), at);
    }

    /** Moderner Beta's world method that says where it snows by its beta climate (null if not found). */
    @Unique private static java.lang.reflect.Method unbeta_betaWeather;
    @Unique private static boolean unbeta_lookedForBetaWeather;

    /**
     * Would it snow here rather than rain? In a beta world, Moderner Beta decides that by its own
     * climate (a Beta Taiga can be snowy while the biome itself says rain), so ask it; otherwise
     * ask the biome as vanilla does.
     */
    @Unique
    private static boolean unbeta_snowsAt(net.minecraft.world.World world, BlockPos at) {
        Biome biome = world.getBiome(at).value();
        if (!unbeta_lookedForBetaWeather) {
            unbeta_lookedForBetaWeather = true;
            try {
                unbeta_betaWeather = world.getClass().getMethod("modernerBeta$samplePrecipitation", Biome.class, BlockPos.class);
            } catch (Throwable t) {
                unbeta_betaWeather = null;
            }
        }
        if (unbeta_betaWeather != null) {
            try {
                Object weather = unbeta_betaWeather.invoke(world, biome, at);
                if (weather instanceof Biome.Precipitation p) return p == Biome.Precipitation.SNOW;
            } catch (Throwable ignored) {
                // fall through to the biome's own answer
            }
        }
        return biome.getPrecipitation(at) == Biome.Precipitation.SNOW;
    }
}
