package net.unbeta.content.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ServerWorldAccess;
import net.unbeta.content.dye.DyedBlocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Terracotta is gone: wherever a structure template (villages included) would place terracotta
 * of any colour, glazed or not, it places dyed Stone of the same colour instead. Plain
 * terracotta, being brown, becomes Brown Stone.
 */
@Mixin(StructureTemplate.class)
public abstract class TerracottaToStoneMixin {

    @Inject(method = "process", at = @At("RETURN"), cancellable = true)
    private static void unbeta_noTerracotta(ServerWorldAccess world, BlockPos pos, BlockPos pivot,
                                            StructurePlacementData placementData,
                                            List<StructureTemplate.StructureBlockInfo> infos,
                                            CallbackInfoReturnable<List<StructureTemplate.StructureBlockInfo>> cir) {
        List<StructureTemplate.StructureBlockInfo> in = cir.getReturnValue();
        List<StructureTemplate.StructureBlockInfo> out = null;
        for (int i = 0; i < in.size(); i++) {
            StructureTemplate.StructureBlockInfo info = in.get(i);
            BlockState stone = replacement(info.state());
            if (stone == null) continue;
            if (out == null) out = new ArrayList<>(in);
            out.set(i, new StructureTemplate.StructureBlockInfo(info.pos(), stone, null));
        }
        if (out != null) cir.setReturnValue(out);
    }

    private static BlockState replacement(BlockState state) {
        String path = Registries.BLOCK.getId(state.getBlock()).getPath();
        if (!path.endsWith("terracotta")) return null;
        if (path.equals("terracotta")) return DyedBlocks.block(DyedBlocks.Shape.STONE, DyeColor.BROWN).getDefaultState();
        String name = path.replace("_glazed_terracotta", "").replace("_terracotta", "");
        DyeColor color = DyeColor.byName(name, null);
        return color == null ? null : DyedBlocks.block(DyedBlocks.Shape.STONE, color).getDefaultState();
    }
}
