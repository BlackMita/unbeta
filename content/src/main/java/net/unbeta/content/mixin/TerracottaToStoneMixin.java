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
 *
 * <p>Also: Stripped Oak Wood (the posts of village street lamps) becomes Stripped Oak Log,
 * standing the same way. And every other block not in Unbeta becomes its nearest Unbeta block
 * (StructureBlocks): stained glass to glass, spruce to oak, job blocks out, and so on.
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
        Boolean abandoned = null;
        for (int i = 0; i < in.size(); i++) {
            StructureTemplate.StructureBlockInfo info = in.get(i);
            // A loot chest in an abandoned village's building may be a mimic instead.
            if (info.state().getBlock() instanceof net.minecraft.block.ChestBlock && info.nbt() != null && info.nbt().contains("LootTable")) {
                if (abandoned == null) abandoned = abandoned(placementData);
                if (abandoned && net.unbeta.content.mimic.MimicChests.abandonedChest(world, info.pos(), info.state())) {
                    if (out == null) out = new ArrayList<>(in);
                    out.set(i, new StructureTemplate.StructureBlockInfo(info.pos(), net.minecraft.block.Blocks.AIR.getDefaultState(), null));
                    continue;
                }
            }
            BlockState stone = replacement(info.state());
            net.minecraft.nbt.NbtCompound nbt = null;
            if (stone == null) {
                // Anything else not in Unbeta: its nearest Unbeta block (see StructureBlocks).
                stone = net.unbeta.content.village.StructureBlocks.swap(info.state());
                if (stone == null) continue;
                if (net.unbeta.content.village.StructureBlocks.keepsData(info.state(), stone)) nbt = info.nbt();
            }
            if (out == null) out = new ArrayList<>(in);
            out.set(i, new StructureTemplate.StructureBlockInfo(info.pos(), stone, nbt));
        }
        if (out != null) cir.setReturnValue(out);
    }

    /** Is this an abandoned village's building? Their block rules scatter cobwebs; nothing else's do. */
    private static boolean abandoned(StructurePlacementData placementData) {
        for (net.minecraft.structure.processor.StructureProcessor p : placementData.getProcessors()) {
            if (!(p instanceof RuleProcessorAccessor rules)) continue;
            for (net.minecraft.structure.processor.StructureProcessorRule r : rules.unbeta_rules()) {
                if (((ProcessorRuleAccessor) r).unbeta_outputState().isOf(net.minecraft.block.Blocks.COBWEB)) return true;
            }
        }
        return false;
    }

    private static BlockState replacement(BlockState state) {
        if (state.isOf(net.minecraft.block.Blocks.STRIPPED_OAK_WOOD)) {
            return net.minecraft.block.Blocks.STRIPPED_OAK_LOG.getDefaultState()
                    .with(net.minecraft.block.PillarBlock.AXIS, state.get(net.minecraft.block.PillarBlock.AXIS));
        }
        String path = Registries.BLOCK.getId(state.getBlock()).getPath();
        if (!path.endsWith("terracotta")) return null;
        if (path.equals("terracotta")) return DyedBlocks.block(DyedBlocks.Shape.STONE, DyeColor.BROWN).getDefaultState();
        String name = path.replace("_glazed_terracotta", "").replace("_terracotta", "");
        DyeColor color = DyeColor.byName(name, null);
        return color == null ? null : DyedBlocks.block(DyedBlocks.Shape.STONE, color).getDefaultState();
    }
}
