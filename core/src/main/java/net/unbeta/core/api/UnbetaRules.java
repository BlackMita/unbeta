package net.unbeta.core.api;

import net.minecraft.block.Block;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.Set;

/**
 * The public, stable query surface of unbeta-core. Phase 2 talks to this and
 * nothing else. Breaking changes here require a major version bump.
 *
 * <p>Every {@code isRemoved} overload is namespace-scoped: content whose
 * namespace is not in the configured gated set always returns {@code false}.
 * This is the single mechanism that keeps ~24 unrelated mods working.
 */
public interface UnbetaRules {

    boolean isRemoved(RuleKey key);

    boolean isRemoved(ContentKind kind, Identifier id);

    boolean isRemoved(EntityType<?> type);

    boolean isRemoved(Item item);

    boolean isRemoved(Block block);

    boolean isDimensionRemoved(RegistryKey<World> world);

    /** Shorthand for {@code isRemoved(RuleKey.system(name))}. */
    boolean isSystemDisabled(String name);

    /** True if this namespace is subject to gating at all. Default: only "minecraft". */
    boolean isGatedNamespace(String namespace);

    /** Full provenance for one rule, for diagnostics. */
    Resolution resolve(RuleKey key);

    /** Every known rule key, sorted. */
    Set<RuleKey> keys();

    /** Register an override at {@link RuleSource#MOD_OVERRIDE} priority. */
    void override(String modId, RuleKey key, boolean removed);
}
