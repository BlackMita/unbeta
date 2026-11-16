package net.unbeta.content.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.structure.processor.StructureProcessorRule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** What a processor rule turns blocks into. */
@Mixin(StructureProcessorRule.class)
public interface ProcessorRuleAccessor {
    @Accessor("outputState")
    BlockState unbeta_outputState();
}
