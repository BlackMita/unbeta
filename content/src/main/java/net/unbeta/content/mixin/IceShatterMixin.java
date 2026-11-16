package net.unbeta.content.mixin;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Ice (plain Ice, not Packed or Blue) may shatter underfoot: each time anything steps onto a new
 * block of it, 1 chance in 16 it breaks to nothing at once. Sneaking across doesn't count as
 * stepping on it (as with magma).
 */
@Mixin(Block.class)
public abstract class IceShatterMixin {

    /** The ice block each thing last stepped on, so standing or sliding on one block rolls only once. */
    @Unique private static final Map<Entity, BlockPos> LAST = new WeakHashMap<>();

    @Inject(method = "onSteppedOn", at = @At("HEAD"))
    private void unbeta_maybeShatter(World world, BlockPos pos, BlockState state, Entity entity, CallbackInfo ci) {
        if (world.isClient || !state.isOf(Blocks.ICE)) return;
        BlockPos last = LAST.get(entity);
        if (pos.equals(last)) return;
        LAST.put(entity, pos.toImmutable());
        if (world.random.nextInt(16) == 0) world.breakBlock(pos, false, entity);
    }
}
