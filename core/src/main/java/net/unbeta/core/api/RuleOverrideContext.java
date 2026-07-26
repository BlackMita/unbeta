package net.unbeta.core.api;

/**
 * Handed to each {@link RuleProvider} so it can register overrides that are
 * attributed back to the mod that made them.
 */
public interface RuleOverrideContext {

    /** The mod id that owns this provider. */
    String modId();

    /**
     * Set a rule. {@code removed = true} means "Unbeta removes/disables this".
     *
     * <p>Phase 2 removes the Nether with a single call:
     * {@code ctx.set(RuleKey.of(ContentKind.DIMENSION, new Identifier("minecraft","the_nether")), true);}
     */
    void set(RuleKey key, boolean removed);

    /** Convenience for the {@code system/unbeta.*} namespace. */
    default void setSystem(String name, boolean removed) {
        set(RuleKey.system(name), removed);
    }
}
