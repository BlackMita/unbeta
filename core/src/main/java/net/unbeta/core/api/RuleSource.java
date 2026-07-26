package net.unbeta.core.api;

/**
 * Where a resolved rule value came from. Later entries win.
 * This ordering is the contract that lets Phase 2 change Phase 1's behaviour
 * without editing Phase 1's code.
 */
public enum RuleSource {
    /** Baked into unbeta-core from manifest.json. */
    MANIFEST_DEFAULT,
    /** config/unbeta/core.json - the user's normal settings file. */
    CORE_CONFIG,
    /** Another mod, via the "unbeta:rules" entrypoint. This is how unbeta-content works. */
    MOD_OVERRIDE,
    /** config/unbeta/overrides.json - always wins, for debugging and power users. */
    USER_OVERRIDE
}
