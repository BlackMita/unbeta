package net.unbeta.content.mixin;

import com.google.common.collect.ImmutableList;
import net.minecraft.structure.processor.RuleStructureProcessor;
import net.minecraft.structure.processor.StructureProcessorRule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads a rule processor's rules (to tell an abandoned village's buildings by their cobwebs). */
@Mixin(RuleStructureProcessor.class)
public interface RuleProcessorAccessor {
    @Accessor("rules")
    ImmutableList<StructureProcessorRule> unbeta_rules();
}
