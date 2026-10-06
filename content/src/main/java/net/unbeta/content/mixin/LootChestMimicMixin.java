package net.unbeta.content.mixin;

import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockView;
import net.minecraft.world.ChunkRegion;
import net.unbeta.content.mimic.MimicChests;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Dungeons and other features give their chests loot through here - each one might be a mimic. */
@Mixin(LootableContainerBlockEntity.class)
public abstract class LootChestMimicMixin {

    @Inject(method = "setLootTable(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/random/Random;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/Identifier;)V",
            at = @At("RETURN"))
    private static void unbeta_maybeMimic(BlockView world, Random random, BlockPos pos, Identifier id, CallbackInfo ci) {
        if (world instanceof ChunkRegion region) MimicChests.tryReplace(region, pos);
    }
}
