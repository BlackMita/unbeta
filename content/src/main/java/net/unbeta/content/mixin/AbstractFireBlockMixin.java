package net.unbeta.content.mixin;

import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.unbeta.content.UnbetaContent;
import net.unbeta.content.block.ObsidianFireBlock;
import net.unbeta.core.api.UnbetaApi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Reroutes fire placement so that igniting over obsidian yields our coloured
 * ObsidianFireBlock instead of regular fire.
 *
 * <p>Vanilla's static {@code AbstractFireBlock.getState(BlockView, BlockPos)} already
 * does exactly this pattern for soul fire (soul soil/sand below -> SoulFireBlock). We
 * inject at HEAD and, if the block below is obsidian, return a random-coloured
 * ObsidianFireBlock state, short-circuiting the vanilla orange/soul decision.
 *
 * <p>Gated by the obsidian_fire system rule so it is inert unless Phase 2 enables it.
 *
 * <p>UNVERIFIED: getState signature assumed to be
 * {@code (BlockView world, BlockPos pos) -> BlockState}. Verify against sources; if the
 * descriptor differs, Mixin will warn at build time (as with the daylight work).
 */
@Mixin(AbstractFireBlock.class)
public abstract class AbstractFireBlockMixin {

    @Inject(method = "getState(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/BlockState;",
            at = @At("HEAD"), cancellable = true)
    private static void unbeta$obsidianFire(BlockView world, BlockPos pos,
                                            CallbackInfoReturnable<BlockState> cir) {
        if (!UnbetaApi.isReady()) return;
        if (!UnbetaApi.rules().isSystemDisabled("obsidian_fire")) return; // rule "on" = feature active
        if (UnbetaContent.OBSIDIAN_FIRE == null) return;

        if (ObsidianFireBlock.isObsidianBelow(world, pos)) {
            // Use the world's random if available; fall back to a fresh one.
            net.minecraft.util.math.random.Random random =
                    (world instanceof net.minecraft.world.World w) ? w.getRandom()
                            : net.minecraft.util.math.random.Random.create();
            cir.setReturnValue(UnbetaContent.OBSIDIAN_FIRE.withRandomColor(random));
        }
    }
}
