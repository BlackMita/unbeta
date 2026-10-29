package net.unbeta.content.mixin;

import net.minecraft.block.PistonBlock;
import net.minecraft.block.piston.PistonHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.unbeta.content.glow.Glow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/** A glowing block pushed or pulled by a piston keeps its glow. */
@Mixin(PistonBlock.class)
public abstract class GlowPistonMixin {

    private static final ThreadLocal<ServerWorld> UNBETA_WORLD = new ThreadLocal<>();
    private static final ThreadLocal<List<BlockPos>> UNBETA_GLOWING = new ThreadLocal<>();

    @Inject(method = "move", at = @At("HEAD"))
    private void unbeta_glowMoveStart(World world, BlockPos pos, Direction dir, boolean retract, CallbackInfoReturnable<Boolean> cir) {
        UNBETA_WORLD.set(world instanceof ServerWorld sw && Glow.anyServer(sw) ? sw : null);
        UNBETA_GLOWING.remove();
    }

    /** Before anything moves: note which of the blocks about to move are glowing. */
    @Redirect(method = "move", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/piston/PistonHandler;getMovedBlocks()Ljava/util/List;"))
    private List<BlockPos> unbeta_glowMoved(PistonHandler handler) {
        List<BlockPos> moved = handler.getMovedBlocks();
        ServerWorld sw = UNBETA_WORLD.get();
        if (sw != null) {
            List<BlockPos> glowing = new ArrayList<>();
            for (BlockPos p : moved) if (Glow.glows(sw, p.asLong())) glowing.add(p.toImmutable());
            if (!glowing.isEmpty()) UNBETA_GLOWING.set(glowing);
        }
        return moved;
    }

    @Inject(method = "move", at = @At("RETURN"))
    private void unbeta_glowMoveEnd(World world, BlockPos pos, Direction dir, boolean retract, CallbackInfoReturnable<Boolean> cir) {
        List<BlockPos> glowing = UNBETA_GLOWING.get();
        ServerWorld sw = UNBETA_WORLD.get();
        UNBETA_GLOWING.remove();
        UNBETA_WORLD.remove();
        if (glowing == null || sw == null || !cir.getReturnValueZ()) return;
        Direction motion = retract ? dir : dir.getOpposite();
        for (BlockPos from : glowing) Glow.remove(sw, from);
        for (BlockPos from : glowing) Glow.add(sw, from.offset(motion));
    }
}
