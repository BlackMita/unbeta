package net.unbeta.content.mixin;

import net.minecraft.entity.EntityData;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.CaveSpiderEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.unbeta.content.spider.SpiderBabies;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A naturally spawning spider has a 1 in 8 chance to be a baby (cave spider) instead. Only
 * NATURAL spawns roll - not spawn eggs, /summon or chunk reloads. The spider is marked here,
 * during spawn setup; SpiderBabies swaps it the moment it enters the world, unseen.
 */
@Mixin(SpiderEntity.class)
public abstract class SpiderSpawnSwapMixin {

    @Inject(method = "initialize", at = @At("HEAD"))
    private void unbeta_maybeBaby(ServerWorldAccess world, LocalDifficulty difficulty,
                                  SpawnReason spawnReason, EntityData entityData,
                                  NbtCompound entityNbt, CallbackInfoReturnable<EntityData> cir) {
        if (spawnReason != SpawnReason.NATURAL) return;
        SpiderEntity self = (SpiderEntity)(Object)this;
        if (self instanceof CaveSpiderEntity) return;
        if (self.getRandom().nextInt(SpiderBabies.SWAP_ODDS) != 0) return;
        self.addCommandTag(SpiderBabies.SWAP_TAG);
    }
}
